package com.sympsel.controller;

import com.sympsel.dto.CommentRequest;
import com.sympsel.dto.CommentResponse;
import com.sympsel.entitys.Comment;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.CommentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<CommentResponse> create(@RequestBody CommentRequest request) {
        Comment comment = commentService.create(UserContext.currentUuid(), request.content(), request.parentUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment));
    }

    @GetMapping
    public List<CommentResponse> list() {
        return commentService.findAll().stream().map(CommentResponse::from).toList();
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<CommentResponse> getByUuid(@PathVariable String uuid) {
        return commentService.findByUuid(uuid)
                .map(comment -> ResponseEntity.ok(CommentResponse.from(comment)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{uuid}/replies")
    public List<CommentResponse> replies(@PathVariable String uuid) {
        return commentService.findReplies(uuid).stream().map(CommentResponse::from).toList();
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<CommentResponse> update(@PathVariable String uuid, @RequestBody CommentRequest request) {
        Comment comment = commentService.update(uuid, request.content());
        return ResponseEntity.ok(CommentResponse.from(comment));
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        commentService.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
