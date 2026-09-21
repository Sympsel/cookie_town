package com.sympsel.controller;

import com.sympsel.dto.*;
import com.sympsel.entitys.Comment;
import com.sympsel.entitys.MessageBoard;
import com.sympsel.entitys.enums.Permission;
import com.sympsel.security.RequirePermission;
import com.sympsel.security.UserContext;
import com.sympsel.service.CommentService;
import com.sympsel.service.MessageBoardService;
import com.sympsel.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/message-boards")
public class MessageBoardController {
    private final MessageBoardService messageBoardService;
    private final UserService userService;
    private final CommentService commentService;

    public MessageBoardController(MessageBoardService messageBoardService, UserService userService, CommentService commentService) {
        this.messageBoardService = messageBoardService;
        this.userService = userService;
        this.commentService = commentService;
    }

    private MessageBoardResponse resp(MessageBoard messageBoard) {
        String name = messageBoard.getPublisherUuid() == null ? null
                : userService.findNamesByUuidIn(List.of(messageBoard.getPublisherUuid())).get(messageBoard.getPublisherUuid());
        return MessageBoardResponse.from(messageBoard, name);
    }

    private Map<String, String> publisherNames(List<Comment> list) {
        return userService.findNamesByUuidIn(list.stream().map(Comment::getPublisherUuid).toList());
    }

//    @PostMapping
//    @RequirePermission({Permission.Common, Permission.Admin})
//    public ResponseEntity<MessageBoardResponse> create(@RequestBody MessageBoardRequest request) {
//        MessageBoard messageBoard = messageBoardService.create(UserContext.currentUuid(), request.content());
//        return ResponseEntity.status(HttpStatus.CREATED).body(resp(messageBoard));
//    }

//    @GetMapping
//    public PageResponse<MessageBoardResponse> list(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(required = false) Integer size) {
//        Pageable pageable = PageUtil.desc(page, size, "createTime");
//        return PageResponse.from(messageBoardService.findAll(pageable), this::resp);
//    }

    @GetMapping("/{uuid}")
    public ResponseEntity<MessageBoardResponse> getByUuid(@PathVariable String uuid) {
        return messageBoardService.findByUuid(uuid)
                .map(messageBoard -> ResponseEntity.ok(resp(messageBoard)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<MessageBoardResponse> update(@PathVariable String uuid, @RequestBody MessageBoardRequest request) {
        MessageBoard messageBoard = messageBoardService.update(uuid, request.content());
        return ResponseEntity.ok(resp(messageBoard));
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        messageBoardService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    /** 留言的回复：返回完整评论（含用户名、@对象），不再是裸 uuid */
    @GetMapping("/{uuid}/replies")
    public List<CommentResponse> replies(@PathVariable String uuid) {
        List<String> ids = messageBoardService.findReplyCommentUuids(uuid);
        if (ids.isEmpty()) return List.of();
        Map<String, Comment> map = commentService.findAllByUuids(ids).stream()
                .collect(Collectors.toMap(Comment::getUuid, Function.identity()));
        List<Comment> ordered = ids.stream().map(map::get).filter(Objects::nonNull).toList();
        return commentRespBatch(ordered);
    }

    /** 回复留言：创建一条 parentUuid=null 的根评论并挂到留言下 */
    @PostMapping("/{uuid}/replies")
    @RequirePermission({Permission.Common, Permission.Admin})
    public ResponseEntity<CommentResponse> addReply(@PathVariable String uuid, @RequestBody CommentRequest request) {
        if (messageBoardService.findByUuid(uuid).isEmpty()) {
            throw new IllegalArgumentException("留言不存在: " + uuid);
        }
        Comment comment = commentService.create(UserContext.currentUuid(), request.content(), null, null, null);
        messageBoardService.addReplyComment(uuid, comment.getUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(commentRespBatch(List.of(comment)).getFirst());
    }

    private List<CommentResponse> commentRespBatch(List<Comment> list) {
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
