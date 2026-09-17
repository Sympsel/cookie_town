package com.sympsel.controller;

import com.sympsel.dto.PageResponse;
import com.sympsel.dto.TownRequest;
import com.sympsel.dto.TownResponse;
import com.sympsel.dto.UserResponse;
import com.sympsel.entitys.Town;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.FileStorageService;
import com.sympsel.service.TownService;
import com.sympsel.utils.PageUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/towns")
public class TownController {
    private final TownService townService;
    private final FileStorageService fileStorageService;

    public TownController(TownService townService, FileStorageService fileStorageService) {
        this.townService = townService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> create(@RequestBody TownRequest request) {
        Town town = townService.create(request.name(), UserContext.currentUuid(), request.description(), request.parentTownUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(TownResponse.from(town));
    }

    @GetMapping
    public PageResponse<TownResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        Pageable pageable = PageUtil.desc(page, size, "createTime");
        return PageResponse.from(townService.findAll(pageable), TownResponse::from);
    }

    /**
     * 主镇（无父镇的根镇）信息，供主页渲染简介。无主镇时返回 404，前端优雅降级隐藏该区块。
     * 轮播图另经 GET /api/towns/{uuid}/pictures 获取（TownResponse 不含懒加载集合）。
     */
    @GetMapping("/main")
    public ResponseEntity<TownResponse> main() {
        return townService.findMain()
                .map(town -> ResponseEntity.ok(TownResponse.from(town)))
                .orElse(ResponseEntity.notFound().build());
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
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> addMember(@PathVariable String uuid, @PathVariable String userUuid) {
        Town town = townService.addMember(uuid, userUuid);
        return ResponseEntity.ok(TownResponse.from(town));
    }

    @DeleteMapping("/{uuid}/members/{userUuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> removeMember(@PathVariable String uuid, @PathVariable String userUuid) {
        Town town = townService.removeMember(uuid, userUuid);
        return ResponseEntity.ok(TownResponse.from(town));
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> update(@PathVariable String uuid, @RequestBody TownRequest request) {
        Town town = townService.update(uuid, request.name(), request.description());
        return ResponseEntity.ok(TownResponse.from(town));
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        townService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{uuid}/members")
    public List<UserResponse> members(@PathVariable String uuid) {
        return townService.findMembers(uuid).stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{uuid}/pictures")
    public List<String> pictures(@PathVariable String uuid) {
        return townService.findPictures(uuid);
    }

    @PostMapping("/{uuid}/pictures")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> addPicture(@PathVariable String uuid, @RequestParam String url) {
        Town town = townService.addPicture(uuid, url);
        return ResponseEntity.ok(TownResponse.from(town));
    }

    /**
     * 上传本地图片作为小镇轮播图：保存到 uploads/towns/{uuid}/，
     * 并把返回的站内 URL 追加到图片列表（复用 addPicture，属主或管理员）。
     */
    @PostMapping("/{uuid}/pictures/upload")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> uploadPicture(@PathVariable String uuid,
                                                      @RequestParam("file") MultipartFile file) {
        if (townService.findByUuid(uuid).isEmpty()) {
            throw new IllegalArgumentException("小镇不存在: " + uuid);
        }
        String url = fileStorageService.store(file, "towns/" + uuid);
        Town town = townService.addPicture(uuid, url);
        return ResponseEntity.ok(TownResponse.from(town));
    }

    @DeleteMapping("/{uuid}/pictures")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> removePicture(@PathVariable String uuid, @RequestParam String url) {
        Town town = townService.removePicture(uuid, url);
        return ResponseEntity.ok(TownResponse.from(town));
    }
}
