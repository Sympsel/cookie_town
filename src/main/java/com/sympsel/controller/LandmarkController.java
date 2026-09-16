package com.sympsel.controller;

import com.sympsel.dto.LandmarkRequest;
import com.sympsel.dto.LandmarkResponse;
import com.sympsel.dto.UserResponse;
import com.sympsel.entitys.Landmark;
import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.entitys.metadatas.Coordinate;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.LandmarkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/landmarks")
public class LandmarkController {
    private final LandmarkService landmarkService;

    public LandmarkController(LandmarkService landmarkService) {
        this.landmarkService = landmarkService;
    }

    @PostMapping
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> create(@RequestBody LandmarkRequest request) {
        Landmark landmark = landmarkService.create(UserContext.currentUuid(), request.name(), request.type(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(LandmarkResponse.from(landmark));
    }

    @GetMapping
    public List<LandmarkResponse> list() {
        return landmarkService.findAll().stream().map(LandmarkResponse::from).toList();
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<LandmarkResponse> getByUuid(@PathVariable String uuid) {
        return landmarkService.findByUuid(uuid)
                .map(landmark -> ResponseEntity.ok(LandmarkResponse.from(landmark)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{uuid}/children")
    public List<LandmarkResponse> children(@PathVariable String uuid) {
        return landmarkService.findChildren(uuid).stream().map(LandmarkResponse::from).toList();
    }

    @PutMapping("/{uuid}/status")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> updateStatus(@PathVariable String uuid, @RequestParam LandmarkStatus status) {
        Landmark landmark = landmarkService.updateStatus(uuid, status);
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> update(@PathVariable String uuid, @RequestBody LandmarkRequest request) {
        Landmark landmark = landmarkService.update(uuid, request.name(), request.type(), request.description());
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        landmarkService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uuid}/builders/{userUuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> addBuilder(@PathVariable String uuid, @PathVariable String userUuid) {
        Landmark landmark = landmarkService.addBuilder(uuid, userUuid);
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @DeleteMapping("/{uuid}/builders/{userUuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> removeBuilder(@PathVariable String uuid, @PathVariable String userUuid) {
        Landmark landmark = landmarkService.removeBuilder(uuid, userUuid);
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @GetMapping("/{uuid}/builders")
    public List<UserResponse> builders(@PathVariable String uuid) {
        return landmarkService.findBuilders(uuid).stream().map(UserResponse::from).toList();
    }

    @PostMapping("/{uuid}/coordinates")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> addCoordinate(@PathVariable String uuid, @RequestBody Coordinate coordinate) {
        Landmark landmark = landmarkService.addCoordinate(uuid, coordinate);
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @DeleteMapping("/{uuid}/coordinates")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> removeCoordinate(@PathVariable String uuid, @RequestBody Coordinate coordinate) {
        Landmark landmark = landmarkService.removeCoordinate(uuid, coordinate);
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @GetMapping("/{uuid}/coordinates")
    public List<Coordinate> coordinates(@PathVariable String uuid) {
        return landmarkService.findCoordinates(uuid);
    }

    @PostMapping("/{uuid}/pictures")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> addPicture(@PathVariable String uuid, @RequestParam String url) {
        Landmark landmark = landmarkService.addPicture(uuid, url);
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @DeleteMapping("/{uuid}/pictures")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> removePicture(@PathVariable String uuid, @RequestParam String url) {
        Landmark landmark = landmarkService.removePicture(uuid, url);
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @GetMapping("/{uuid}/pictures")
    public List<String> pictures(@PathVariable String uuid) {
        return landmarkService.findPictures(uuid);
    }
}
