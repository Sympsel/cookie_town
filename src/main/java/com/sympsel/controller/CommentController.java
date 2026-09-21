package com.sympsel.controller;

import com.sympsel.dto.CommentRequest;
import com.sympsel.dto.CommentResponse;
import com.sympsel.dto.PageResponse;
import com.sympsel.entitys.Comment;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.CommentService;
import com.sympsel.service.UserService;
import com.sympsel.utils.PageUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    private final CommentService commentService;
    private final UserService userService;

    public CommentController(CommentService commentService, UserService userService) {
        this.commentService = commentService;
        this.userService = userService;
    }

    private CommentResponse resp(Comment comment) {
        String publisherName = comment.getPublisherUuid() == null
                ? null
                : userService.findNamesByUuidIn(List.of(comment.getPublisherUuid())).get(comment.getPublisherUuid());
        String replyToName = comment.getReplyToUuid() == null
                ? null
                : userService.findNamesByUuidIn(List.of(comment.getReplyToUuid())).get(comment.getReplyToUuid());
        return CommentResponse.from(comment, publisherName, replyToName);
    }

    private Map<String, String> publisherNames(List<Comment> list) {
        return userService.findNamesByUuidIn(list.stream().map(Comment::getPublisherUuid).toList());
    }

    @PostMapping
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<CommentResponse> create(@RequestBody CommentRequest request) {
        if (request.parentUuid() == null || request.parentUuid().isBlank()) {
            throw new IllegalArgumentException("请从地标或留言的回复入口发表评论");
        }
        Comment comment = commentService.create(
                UserContext.currentUuid(), request.content(), request.parentUuid(), request.replyToUuid(), request.score()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(resp(comment));
    }

//    @GetMapping
//    public PageResponse<CommentResponse> list(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(required = false) Integer size) {
//        Pageable pageable = PageUtil.desc(page, size, "createTime");
//        return PageResponse.from(commentService.findAll(pageable), this::resp);
//    }

    @GetMapping("/{uuid}")
    public ResponseEntity<CommentResponse> getByUuid(@PathVariable String uuid) {
        return commentService.findByUuid(uuid)
                .map(comment -> ResponseEntity.ok(resp(comment)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{uuid}/replies")
    public List<CommentResponse> replies(@PathVariable String uuid) {
        return respBatch(commentService.findReplies(uuid));
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<CommentResponse> update(@PathVariable String uuid, @RequestBody CommentRequest request) {
        Comment comment = commentService.update(uuid, request.content());
        return ResponseEntity.ok(resp(comment));
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        commentService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    private List<CommentResponse> respBatch(List<Comment> list) {
        Map<String, Comment> replyToMap = commentService.findAllByUuids(
                list.stream().map(Comment::getReplyToUuid).filter(Objects::nonNull).toList()
        ).stream().collect(Collectors.toMap(Comment::getUuid, Function.identity()));

        Set<String> uuids = new HashSet<>();
        list.forEach(c -> {
            if (c.getPublisherUuid() != null) uuids.add(c.getPublisherUuid());
            Comment rt = c.getReplyToUuid() == null ? null : replyToMap.get(c.getReplyToUuid());
            if (rt != null && rt.getPublisherUuid() != null) uuids.add(rt.getPublisherUuid());
        });
        Map<String, String> names = userService.findNamesByUuidIn(List.copyOf(uuids));

        return list.stream().map(c -> {
            Comment rt = c.getReplyToUuid() == null ? null : replyToMap.get(c.getReplyToUuid());
            String replyToName = rt == null ? null : names.get(rt.getPublisherUuid());
            return CommentResponse.from(c, names.get(c.getPublisherUuid()), replyToName);
        }).toList();
    }
}
