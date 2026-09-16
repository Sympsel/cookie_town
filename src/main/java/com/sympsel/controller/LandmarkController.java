package com.sympsel.controller;

import com.sympsel.dto.LandmarkRequest;
import com.sympsel.dto.LandmarkResponse;
import com.sympsel.dto.UserResponse;
import com.sympsel.entitys.Landmark;
import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.metadatas.Coordinate;
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
    public ResponseEntity<LandmarkResponse> updateStatus(@PathVariable String uuid, @RequestParam LandmarkStatus status) {
        Landmark landmark = landmarkService.updateStatus(uuid, status);
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<LandmarkResponse> update(@PathVariable String uuid, @RequestBody LandmarkRequest request) {
        Landmark landmark = landmarkService.update(uuid, request.name(), request.type(), request.description());
        return ResponseEntity.ok(LandmarkResponse.from(landmark));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        landmarkService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{uuid}/builders")
    public List<UserResponse> builders(@PathVariable String uuid) {
        return landmarkService.findBuilders(uuid).stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{uuid}/coordinates")
    public List<Coordinate> coordinates(@PathVariable String uuid) {
        return landmarkService.findCoordinates(uuid);
    }

    @GetMapping("/{uuid}/pictures")
    public List<String> pictures(@PathVariable String uuid) {
        return landmarkService.findPictures(uuid);
    }
}
