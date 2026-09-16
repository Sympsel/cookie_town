package com.sympsel.controller;

import com.sympsel.dto.TownRequest;
import com.sympsel.dto.TownResponse;
import com.sympsel.dto.UserResponse;
import com.sympsel.entitys.Town;
import com.sympsel.security.UserContext;
import com.sympsel.service.TownService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/towns")
public class TownController {
    private final TownService townService;

    public TownController(TownService townService) {
        this.townService = townService;
    }

    @PostMapping
    public ResponseEntity<TownResponse> create(@RequestBody TownRequest request) {
        Town town = townService.create(request.name(), UserContext.currentUuid(), request.description(), request.parentTownUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(TownResponse.from(town));
    }

    @GetMapping
    public List<TownResponse> list() {
        return townService.findAll().stream().map(TownResponse::from).toList();
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<TownResponse> getByUuid(@PathVariable String uuid) {
        return townService.findByUuid(uuid)
                .map(town -> ResponseEntity.ok(TownResponse.from(town)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{uuid}/children")
    public List<TownResponse> children(@PathVariable String uuid) {
        return townService.findChildren(uuid).stream().map(TownResponse::from).toList();
    }

    @PostMapping("/{uuid}/members/{userUuid}")
    public ResponseEntity<TownResponse> addMember(@PathVariable String uuid, @PathVariable String userUuid) {
        Town town = townService.addMember(uuid, userUuid);
        return ResponseEntity.ok(TownResponse.from(town));
    }

    @DeleteMapping("/{uuid}/members/{userUuid}")
    public ResponseEntity<TownResponse> removeMember(@PathVariable String uuid, @PathVariable String userUuid) {
        Town town = townService.removeMember(uuid, userUuid);
        return ResponseEntity.ok(TownResponse.from(town));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<TownResponse> update(@PathVariable String uuid, @RequestBody TownRequest request) {
        Town town = townService.update(uuid, request.name(), request.description());
        return ResponseEntity.ok(TownResponse.from(town));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        townService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{uuid}/members")
    public List<UserResponse> members(@PathVariable String uuid) {
        return townService.findMembers(uuid).stream().map(UserResponse::from).toList();
    }
}
