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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/towns")
public class TownController {
    private final TownService townService;
    private final FileStorageService fileStorageService;

    public TownController(TownService townService, FileStorageService fileStorageService) {
        this.townService = townService;
        this.fileStorageService = fileStorageService;
    }

    /** 组装单个小镇的响应：镇长字段附带解析出的用户名（解析不到则为 null）。 */
    private TownResponse resp(Town town) {
        String ownerName = town.getOwnerUuid() == null
                ? null
                : townService.findNamesByUuids(List.of(town.getOwnerUuid())).get(town.getOwnerUuid());
        return TownResponse.from(town, ownerName);
    }

    /** 批量组装：一次查询解析所有镇长用户名，避免逐条 N+1。 */
    private Map<String, String> ownerNames(List<Town> towns) {
        return townService.findNamesByUuids(towns.stream().map(Town::getOwnerUuid).toList());
    }

    @PostMapping
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> create(@RequestBody TownRequest request) {
        Town town = townService.create(request.name(), UserContext.currentUuid(), request.description(), request.parentTownUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp(town));
    }

    @GetMapping
    public PageResponse<TownResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        Pageable pageable = PageUtil.desc(page, size, "createTime");
        Page<Town> towns = townService.findAll(pageable);
        Map<String, String> names = ownerNames(towns.getContent());
        return PageResponse.from(towns, t -> TownResponse.from(t, names.get(t.getOwnerUuid())));
    }

    /**
     * 主镇（无父镇的根镇）信息，供主页渲染简介。无主镇时返回 404，前端优雅降级隐藏该区块。
     * 图片另经 GET /api/towns/{uuid}/pictures 获取（TownResponse 不含懒加载集合）。
     */
    @GetMapping("/main")
    public ResponseEntity<TownResponse> main() {
        return townService.findMain()
                .map(town -> ResponseEntity.ok(resp(town)))
                .orElse(ResponseEntity.notFound().build());
    }


    @GetMapping("/{uuid}")
    public ResponseEntity<TownResponse> getByUuid(@PathVariable String uuid) {
        return townService.findByUuid(uuid)
                .map(town -> ResponseEntity.ok(resp(town)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{uuid}/children")
    public List<TownResponse> children(@PathVariable String uuid) {
        List<Town> children = townService.findChildren(uuid);
        Map<String, String> names = ownerNames(children);
        return children.stream().map(t -> TownResponse.from(t, names.get(t.getOwnerUuid()))).toList();
    }

    @PostMapping("/{uuid}/members/{userUuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> addMember(@PathVariable String uuid, @PathVariable String userUuid) {
        Town town = townService.addMember(uuid, userUuid);
        return ResponseEntity.ok(resp(town));
    }

    @DeleteMapping("/{uuid}/members/{userUuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> removeMember(@PathVariable String uuid, @PathVariable String userUuid) {
        Town town = townService.removeMember(uuid, userUuid);
        return ResponseEntity.ok(resp(town));
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> update(@PathVariable String uuid, @RequestBody TownRequest request) {
        Town town = townService.update(uuid, request.name(), request.description());
        return ResponseEntity.ok(resp(town));
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
        return ResponseEntity.ok(resp(town));
    }

    /**
     * 上传本地图片作为小镇图片：保存到 uploads/towns/{uuid}/，
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
        return ResponseEntity.ok(resp(town));
    }

    @DeleteMapping("/{uuid}/pictures")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<TownResponse> removePicture(@PathVariable String uuid, @RequestParam String url) {
        Town town = townService.removePicture(uuid, url);
        return ResponseEntity.ok(resp(town));
    }
}