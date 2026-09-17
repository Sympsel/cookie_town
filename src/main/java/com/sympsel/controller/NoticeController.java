package com.sympsel.controller;

import com.sympsel.dto.NoticeRequest;
import com.sympsel.dto.NoticeResponse;
import com.sympsel.dto.PageResponse;
import com.sympsel.entitys.Notice;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.NoticeService;
import com.sympsel.utils.PageUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @PostMapping
    @RequirePermission(Permission.Admin)
    public ResponseEntity<NoticeResponse> publish(@RequestBody NoticeRequest request) {
        Notice notice = noticeService.publish(UserContext.currentUuid(), request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(NoticeResponse.from(notice));
    }

    @GetMapping
    public PageResponse<NoticeResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = PageUtil.desc(page, size, "publishTime");
        return PageResponse.from(noticeService.findAll(pageable), NoticeResponse::from);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<NoticeResponse> getByUuid(@PathVariable String uuid) {
        return noticeService.findByUuid(uuid)
                .map(notice -> ResponseEntity.ok(NoticeResponse.from(notice)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{uuid}")
    @RequirePermission(Permission.Admin)
    public ResponseEntity<NoticeResponse> update(@PathVariable String uuid, @RequestBody NoticeRequest request) {
        Notice notice = noticeService.update(uuid, request.title(), request.content());
        return ResponseEntity.ok(NoticeResponse.from(notice));
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission(Permission.Admin)
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        noticeService.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
