package com.sympsel.controller;

import com.sympsel.dto.*;
import com.sympsel.entitys.Comment;
import com.sympsel.entitys.Landmark;
import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.entitys.metadatas.Coordinate;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.CommentService;
import com.sympsel.service.FileStorageService;
import com.sympsel.service.LandmarkService;
import com.sympsel.service.UserService;
import com.sympsel.utils.PageUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/landmarks")
public class LandmarkController {
    private final LandmarkService landmarkService;
    private final FileStorageService fileStorageService;
    private final CommentService commentService;
    private final UserService userService;

    public LandmarkController(LandmarkService landmarkService, FileStorageService fileStorageService, CommentService commentService, UserService userService) {
        this.landmarkService = landmarkService;
        this.fileStorageService = fileStorageService;
        this.commentService = commentService;
        this.userService = userService;
    }

    @PostMapping
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> create(@RequestBody LandmarkRequest request) {
        Landmark landmark = landmarkService.create(UserContext.currentUuid(), request.name(), request.type(), request.description(), request.pictures());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp(landmark));
    }

    private LandmarkResponse resp(Landmark landmark) {
        String submitterName = landmark.getSubmitterUuid() == null
                ? null
                : userService.findNamesByUuidIn(
                        List.of(landmark.getSubmitterUuid())).get(landmark.getSubmitterUuid());
        return LandmarkResponse.from(landmark, submitterName);
    }

    private Map<String, String> submitterNames(List<Landmark> landmarks) {
        return userService.findNamesByUuidIn(landmarks.stream().map(Landmark::getSubmitterUuid).distinct().toList());
    }

    @GetMapping
    public PageResponse<LandmarkResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = PageUtil.desc(page, size, "createTime");
        return PageResponse.from(landmarkService.findAll(pageable), this::resp);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<LandmarkResponse> getByUuid(@PathVariable String uuid) {
        return landmarkService.findByUuid(uuid)
                .map(landmark -> ResponseEntity.ok(resp(landmark)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{uuid}/children")
    public List<LandmarkResponse> children(@PathVariable String uuid) {
        return landmarkService.findChildren(uuid).stream().map(this::resp).toList();
    }

    @PutMapping("/{uuid}/status")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> updateStatus(@PathVariable String uuid, @RequestParam LandmarkStatus status) {
        Landmark landmark = landmarkService.updateStatus(uuid, status);
        return ResponseEntity.ok(resp(landmark));
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> update(@PathVariable String uuid, @RequestBody LandmarkRequest request) {
        Landmark landmark = landmarkService.update(uuid, request.name(), request.type(), request.description());
        return ResponseEntity.ok(resp(landmark));
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
        return ResponseEntity.ok(resp(landmark));
    }

    @DeleteMapping("/{uuid}/builders/{userUuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> removeBuilder(@PathVariable String uuid, @PathVariable String userUuid) {
        Landmark landmark = landmarkService.removeBuilder(uuid, userUuid);
        return ResponseEntity.ok(resp(landmark));
    }

    @GetMapping("/{uuid}/builders")
    public List<UserResponse> builders(@PathVariable String uuid) {
        return landmarkService.findBuilders(uuid).stream().map(UserResponse::from).toList();
    }

    @PostMapping("/{uuid}/coordinates")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> addCoordinate(@PathVariable String uuid, @RequestBody Coordinate coordinate) {
        Landmark landmark = landmarkService.addCoordinate(uuid, coordinate);
        return ResponseEntity.ok(resp(landmark));
    }

    @DeleteMapping("/{uuid}/coordinates")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> removeCoordinate(@PathVariable String uuid, @RequestBody Coordinate coordinate) {
        Landmark landmark = landmarkService.removeCoordinate(uuid, coordinate);
        return ResponseEntity.ok(resp(landmark));
    }

    @GetMapping("/{uuid}/coordinates")
    public List<Coordinate> coordinates(@PathVariable String uuid) {
        return landmarkService.findCoordinates(uuid);
    }

    @PostMapping("/{uuid}/pictures")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> addPicture(@PathVariable String uuid, @RequestParam String url) {
        Landmark landmark = landmarkService.addPicture(uuid, url);
        return ResponseEntity.ok(resp(landmark));
    }

    /**
     * 上传本地图片文件作为地标图片：保存到 uploads/landmarks/{uuid}/，
     * 并把返回的站内 URL 追加到该地标的图片列表（复用 addPicture）。
     */
    @PostMapping("/{uuid}/pictures/upload")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> uploadPicture(@PathVariable String uuid,
                                                          @RequestParam("file") MultipartFile file) {
        if (landmarkService.findByUuid(uuid).isEmpty()) {
            throw new IllegalArgumentException("地标不存在: " + uuid);
        }
        String url = fileStorageService.store(file, "landmarks/" + uuid);
        Landmark landmark = landmarkService.addPicture(uuid, url);
        return ResponseEntity.ok(resp(landmark));
    }

    @DeleteMapping("/{uuid}/pictures")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> removePicture(@PathVariable String uuid, @RequestParam String url) {
        Landmark landmark = landmarkService.removePicture(uuid, url);
        return ResponseEntity.ok(resp(landmark));
    }

    @GetMapping("/{uuid}/pictures")
    public List<String> pictures(@PathVariable String uuid) {
        return landmarkService.findPictures(uuid);
    }

    @GetMapping("/{uuid}/comments")
    public List<CommentResponse> comments(@PathVariable String uuid) {
        return landmarkService.findComments(uuid).stream().map(CommentResponse::from).toList();
    }

    /**
     * 为地标发表根评论：复用 Comment 系统创建评论，再把评论 uuid 挂到地标评论列表。
     * 回复仍走 POST /api/comments（parentUuid 指向根评论），与留言板一致。
     */
    @PostMapping("/{uuid}/comments")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<LandmarkResponse> addComment(@PathVariable String uuid, @RequestBody CommentRequest request) {
        if (landmarkService.findByUuid(uuid).isEmpty()) {
            throw new IllegalArgumentException("地标不存在: " + uuid);
        }
        Comment comment = commentService.create(UserContext.currentUuid(), request.content(), null, request.score());
        Landmark landmark = landmarkService.addCommentUuid(uuid, comment.getUuid());
        return ResponseEntity.ok(resp(landmark));
    }
}
