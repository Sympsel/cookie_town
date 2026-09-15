package com.sympsel.controller;

import com.sympsel.dto.LandmarkRequest;
import com.sympsel.dto.LandmarkResponse;
import com.sympsel.entitys.Landmark;
import com.sympsel.entitys.enums.LandmarkStatus;
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
        Landmark landmark = landmarkService.create(request.submitterUuid(), request.name(), request.type(), request.description());
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
}
