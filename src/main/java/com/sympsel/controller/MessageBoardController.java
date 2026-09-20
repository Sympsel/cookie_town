package com.sympsel.controller;

import com.sympsel.dto.*;
import com.sympsel.entitys.Comment;
import com.sympsel.entitys.MessageBoard;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.MessageBoardService;
import com.sympsel.service.UserService;
import com.sympsel.utils.PageUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message-boards")
public class MessageBoardController {
    private final MessageBoardService messageBoardService;
    private final UserService userService;

    public MessageBoardController(MessageBoardService messageBoardService, UserService userService) {
        this.messageBoardService = messageBoardService;
        this.userService = userService;
    }

    private MessageBoardResponse resp(MessageBoard messageBoard) {
        String name = messageBoard.getPublisherUuid() == null ? null
                : userService.findNamesByUuidIn(List.of(messageBoard.getPublisherUuid())).get(messageBoard.getPublisherUuid());
        return MessageBoardResponse.from(messageBoard, name);
    }

    private Map<String, String> publisherNames(List<Comment> list) {
        return userService.findNamesByUuidIn(list.stream().map(Comment::getPublisherUuid).toList());
    }

    @PostMapping
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<MessageBoardResponse> create(@RequestBody MessageBoardRequest request) {
        MessageBoard messageBoard = messageBoardService.create(UserContext.currentUuid(), request.content(), request.score());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp(messageBoard));
    }

    @GetMapping
    public PageResponse<MessageBoardResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = PageUtil.desc(page, size, "createTime");
        return PageResponse.from(messageBoardService.findAll(pageable), this::resp);
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<MessageBoardResponse> getByUuid(@PathVariable String uuid) {
        return messageBoardService.findByUuid(uuid)
                .map(messageBoard -> ResponseEntity.ok(resp(messageBoard)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<MessageBoardResponse> update(@PathVariable String uuid, @RequestBody MessageBoardRequest request) {
        MessageBoard messageBoard = messageBoardService.update(uuid, request.content(), request.score());
        return ResponseEntity.ok(resp(messageBoard));
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
