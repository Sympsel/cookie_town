package com.sympsel.controller;

import com.sympsel.dto.MessageBoardRequest;
import com.sympsel.dto.MessageBoardResponse;
import com.sympsel.entitys.MessageBoard;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.MessageBoardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/message-boards")
public class MessageBoardController {
    private final MessageBoardService messageBoardService;

    public MessageBoardController(MessageBoardService messageBoardService) {
        this.messageBoardService = messageBoardService;
    }

    @PostMapping
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<MessageBoardResponse> create(@RequestBody MessageBoardRequest request) {
        MessageBoard messageBoard = messageBoardService.create(UserContext.currentUuid(), request.content(), request.score());
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageBoardResponse.from(messageBoard));
    }

    @GetMapping
    public List<MessageBoardResponse> list() {
        return messageBoardService.findAll().stream().map(MessageBoardResponse::from).toList();
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<MessageBoardResponse> getByUuid(@PathVariable String uuid) {
        return messageBoardService.findByUuid(uuid)
                .map(messageBoard -> ResponseEntity.ok(MessageBoardResponse.from(messageBoard)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<MessageBoardResponse> update(@PathVariable String uuid, @RequestBody MessageBoardRequest request) {
        MessageBoard messageBoard = messageBoardService.update(uuid, request.content(), request.score());
        return ResponseEntity.ok(MessageBoardResponse.from(messageBoard));
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        messageBoardService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{uuid}/replies")
    public List<String> replies(@PathVariable String uuid) {
        return messageBoardService.findReplyCommentUuids(uuid);
    }
}
