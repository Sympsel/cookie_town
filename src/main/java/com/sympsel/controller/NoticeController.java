package com.sympsel.controller;

import com.sympsel.dto.NoticeRequest;
import com.sympsel.dto.NoticeResponse;
import com.sympsel.entitys.Notice;
import com.sympsel.service.NoticeService;
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
    public ResponseEntity<NoticeResponse> publish(@RequestBody NoticeRequest request) {
        Notice notice = noticeService.publish(request.publisherUuid(), request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(NoticeResponse.from(notice));
    }

    @GetMapping
    public List<NoticeResponse> list() {
        return noticeService.findAll().stream().map(NoticeResponse::from).toList();
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<NoticeResponse> getByUuid(@PathVariable String uuid) {
        return noticeService.findByUuid(uuid)
                .map(notice -> ResponseEntity.ok(NoticeResponse.from(notice)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<NoticeResponse> update(@PathVariable String uuid, @RequestBody NoticeRequest request) {
        Notice notice = noticeService.update(uuid, request.title(), request.content());
        return ResponseEntity.ok(NoticeResponse.from(notice));
    }
}
