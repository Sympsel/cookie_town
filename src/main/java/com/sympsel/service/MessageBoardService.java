package com.sympsel.service;

import com.sympsel.entitys.MessageBoard;
import com.sympsel.entitys.enums.Score;
import com.sympsel.repository.CommentRepository;
import com.sympsel.repository.MessageBoardRepository;
import com.sympsel.security.PermissionGuard;
import com.sympsel.utils.UuidUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MessageBoardService {
    private final MessageBoardRepository messageBoardRepository;
    private final CommentRepository commentRepository;

    public MessageBoardService(MessageBoardRepository messageBoardRepository, CommentRepository commentRepository) {
        this.messageBoardRepository = messageBoardRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional(readOnly = true)
    public Page<MessageBoard> findByTown(String townUuid, Pageable pageable) {
        return messageBoardRepository.findByTownUuid(townUuid, pageable);
    }

    @Transactional
    public MessageBoard create(String publisherUuid, String townUuid, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("留言内容不能为空");
        }
        long now = System.currentTimeMillis();
        MessageBoard messageBoard = new MessageBoard();
        messageBoard.setUuid(UuidUtil.generate());
        messageBoard.setPublisherUuid(publisherUuid);
        messageBoard.setTownUuid(townUuid);
        messageBoard.setContent(content);
        messageBoard.setCreateTime(now);
        messageBoard.setUpdateTime(now);
        return messageBoardRepository.save(messageBoard);
    }

    @Transactional(readOnly = true)
    public Optional<MessageBoard> findByUuid(String uuid) {
        return messageBoardRepository.findById(uuid);
    }

    @Transactional(readOnly = true)
    public List<MessageBoard> findAll() {
        return messageBoardRepository.findAllByOrderByCreateTimeDesc();
    }

    @Transactional(readOnly = true)
    public Page<MessageBoard> findAll(Pageable pageable) {
        return messageBoardRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<MessageBoard> findByPublisher(String publisherUuid) {
        return messageBoardRepository.findByPublisherUuid(publisherUuid);
    }

    @Transactional
    public MessageBoard addReplyComment(String uuid, String commentUuid) {
        MessageBoard messageBoard = messageBoardRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("留言不存在: " + uuid));
        if (!messageBoard.getReplyCommentUuids().contains(commentUuid)) {
            messageBoard.getReplyCommentUuids().add(commentUuid);
            messageBoard.setUpdateTime(System.currentTimeMillis());
        }
        return messageBoardRepository.save(messageBoard);
    }

    @Transactional
    public MessageBoard update(String uuid, String content) {
        MessageBoard messageBoard = messageBoardRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("留言不存在: " + uuid));
        PermissionGuard.requireOwnerOrAdmin(messageBoard.getPublisherUuid());
        if (content != null && !content.isBlank()) {
            messageBoard.setContent(content);
        }
        messageBoard.setUpdateTime(System.currentTimeMillis());
        return messageBoardRepository.save(messageBoard);
    }

    @Transactional
    public void delete(String uuid) {
        MessageBoard messageBoard = messageBoardRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("留言不存在: " + uuid));
        if (!messageBoardRepository.existsById(uuid)) {
            throw new IllegalArgumentException("留言不存在: " + uuid);
        }
        PermissionGuard.requireOwnerOrAdmin(messageBoard.getPublisherUuid());
        for (String replyUuid : List.copyOf(messageBoard.getReplyCommentUuids())) {
            commentRepository.deleteAll(commentRepository.findByParentUuidOrderByCreateTimeAsc(replyUuid));
            commentRepository.deleteById(replyUuid);
        }
        messageBoardRepository.deleteById(uuid);
    }

    @Transactional(readOnly = true)
    public  List<String> findReplyCommentUuids(String uuid) {
        MessageBoard messageBoard = messageBoardRepository.findById(uuid).orElseThrow(
                () -> new IllegalArgumentException("留言不存在: " + uuid)
        );
        return List.copyOf(messageBoard.getReplyCommentUuids());
    }
}
