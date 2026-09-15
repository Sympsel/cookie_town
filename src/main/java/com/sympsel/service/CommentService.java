package com.sympsel.service;

import com.sympsel.entitys.Comment;
import com.sympsel.repository.CommentRepository;
import com.sympsel.utils.UuidUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CommentService {
    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Transactional
    public Comment create(String publisherUuid, String content, String parentUuid) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        long now = System.currentTimeMillis();
        Comment comment = new Comment();
        comment.setUuid(UuidUtil.generate());
        comment.setPublisherUuid(publisherUuid);
        comment.setContent(content);
        comment.setParentUuid(parentUuid);
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        Comment saved = commentRepository.save(comment);

        if (parentUuid != null) {
            commentRepository.findById(parentUuid).ifPresent(parent -> {
                parent.getReplyUuids().add(saved.getUuid());
                parent.setUpdateTime(System.currentTimeMillis());
                commentRepository.save(parent);
            });
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Comment> findByUuid(String uuid) {
        return commentRepository.findById(uuid);
    }

    @Transactional(readOnly = true)
    public List<Comment> findAll() {
        return commentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Comment> findByPublisher(String publisherUuid) {
        return commentRepository.findByPublisherUuid(publisherUuid);
    }

    @Transactional(readOnly = true)
    public List<Comment> findReplies(String parentUuid) {
        return commentRepository.findByParentUuid(parentUuid);
    }
}
