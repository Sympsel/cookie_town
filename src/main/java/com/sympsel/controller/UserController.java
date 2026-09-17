package com.sympsel.controller;

import com.sympsel.dto.PageResponse;
import com.sympsel.dto.PasswordResetRequest;
import com.sympsel.dto.RegisterRequest;
import com.sympsel.dto.UserResponse;
import com.sympsel.dto.UserUpdateRequest;
import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.service.FileStorageService;
import com.sympsel.service.UserService;
import com.sympsel.utils.PageUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final FileStorageService fileStorageService;

    public UserController(UserService userService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        User user = userService.register(request.name(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = PageUtil.desc(page, size, "createTime");
        // findAllDto 已在事务内映射为 UserResponse（含 tags），此处 identity 透传
        return PageResponse.from(userService.findAllDto(pageable), Function.identity());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<UserResponse> getByUuid(@PathVariable String uuid) {
        // findDtoByUuid 已在事务内映射为 UserResponse（含 tags），供详情页展示
        return userService.findDtoByUuid(uuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<UserResponse> update(@PathVariable String uuid, @RequestBody UserUpdateRequest request) {
        User user = userService.update(uuid, request.name(), request.introduction());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * 重置密码：仅本人或拥有特权标签的开发者可操作（Service 层校验）。
     * 管理员角色本身不再能修改他人密码。
     */
    @PutMapping("/{uuid}/password")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<Void> resetPassword(@PathVariable String uuid, @RequestBody PasswordResetRequest request) {
        userService.resetPassword(uuid, request.password());
        return ResponseEntity.noContent().build();
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

    /**
     * 管理员为其它玩家添加自定义标签（标签通过查询参数传递）。
     */
    @PostMapping("/{uuid}/tags")
    @RequirePermission({Permission.Admin})
    public List<String> addTag(@PathVariable String uuid, @RequestParam String tag) {
        return userService.addTag(uuid, tag);
    }

    /**
     * 管理员移除其它玩家的自定义标签。
     */
    @DeleteMapping("/{uuid}/tags")
    @RequirePermission({Permission.Admin})
    public List<String> removeTag(@PathVariable String uuid, @RequestParam String tag) {
        return userService.removeTag(uuid, tag);
    }

    /**
     * 上传本地图片作为用户头像：以固定文件名保存到 uploads/avatars/{uuid}.{ext}（同名覆盖），
     * 更新 avatar 字段，并清理被替换掉的旧头像文件。仅本人或管理员可操作。
     */
    @PostMapping("/{uuid}/avatar/upload")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<UserResponse> uploadAvatar(@PathVariable String uuid,
                                                     @RequestParam("file") MultipartFile file) {
        User user = userService.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + uuid));
        String oldUrl = user.getAvatar();
        String newUrl = fileStorageService.store(file, "avatars", uuid);
        User updated = userService.updateAvatar(uuid, newUrl);
        if (oldUrl != null && !oldUrl.equals(newUrl)) {
            fileStorageService.deleteByUrl(oldUrl);
        }
        return ResponseEntity.ok(UserResponse.from(updated));
    }
}