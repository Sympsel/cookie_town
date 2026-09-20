package com.sympsel.controller;

import com.sympsel.dto.NoticeRequest;
import com.sympsel.dto.NoticeResponse;
import com.sympsel.dto.PageResponse;
import com.sympsel.entitys.Comment;
import com.sympsel.entitys.Notice;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.NoticeService;
import com.sympsel.service.UserService;
import com.sympsel.utils.PageUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final NoticeService noticeService;
    private final UserService userService;

    public NoticeController(NoticeService noticeService, UserService userService) {
        this.noticeService = noticeService;
        this.userService = userService;
    }

    private NoticeResponse resp(Notice notice) {
        String name = notice.getPublisherUuid() == null
                ? null
                : userService.findNamesByUuidIn(List.of(notice.getPublisherUuid())).get(notice.getPublisherUuid());
        return NoticeResponse.from(notice, name);
    }

    private Map<String, String> publisherNames(List<Comment> list) {
        return userService.findNamesByUuidIn(list.stream().map(Comment::getPublisherUuid).toList());
    }

    @PostMapping
    @RequirePermission(Permission.Admin)
    public ResponseEntity<NoticeResponse> publish(@RequestBody NoticeRequest request) {
        Notice notice = noticeService.publish(UserContext.currentUuid(), request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp(notice));
    }

    @GetMapping
    public PageResponse<NoticeResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = PageUtil.desc(page, size, "publishTime");
        return PageResponse.from(noticeService.findAll(pageable), this::resp);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<NoticeResponse> getByUuid(@PathVariable String uuid) {
        return noticeService.findByUuid(uuid)
                .map(notice -> ResponseEntity.ok(resp(notice)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{uuid}")
    @RequirePermission(Permission.Admin)
    public ResponseEntity<NoticeResponse> update(@PathVariable String uuid, @RequestBody NoticeRequest request) {
        Notice notice = noticeService.update(uuid, request.title(), request.content());
        return ResponseEntity.ok(resp(notice));
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission(Permission.Admin)
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        noticeService.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
