package com.sympsel.controller;

import com.sympsel.dto.PageResponse;
import com.sympsel.dto.RegisterRequest;
import com.sympsel.dto.UserResponse;
import com.sympsel.dto.UserUpdateRequest;
import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.service.UserService;
import com.sympsel.utils.PageUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        User user = userService.register(request.name(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageUtil.desc(page, size, "createTime");
        return PageResponse.from(userService.findAll(pageable), UserResponse::from);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<UserResponse> getByUuid(@PathVariable String uuid) {
        return userService.findByUuid(uuid)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<UserResponse> update(@PathVariable String uuid, @RequestBody UserUpdateRequest request) {
        User user = userService.update(uuid, request.name(), request.introduction());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission({Permission.Admin})
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        userService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{uuid}/permission")
    @RequirePermission({Permission.Admin})
    public ResponseEntity<UserResponse> updatePermission(@PathVariable String uuid, @RequestParam Permission permission) {
        User user = userService.updatePermission(uuid, permission);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/{uuid}/tags")
    public List<String> tags(@PathVariable String uuid) {
        return userService.findTags(uuid);
    }
}