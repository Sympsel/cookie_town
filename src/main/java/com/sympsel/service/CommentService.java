package com.sympsel.service;

import com.sympsel.entitys.Comment;
import com.sympsel.entitys.enums.Score;
import com.sympsel.repository.CommentRepository;
import com.sympsel.security.PermissionGuard;
import com.sympsel.utils.UuidUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class CommentService {
    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Transactional
    public Comment create(String publisherUuid, String content, String parentUuid, String replyToUuid, Score score) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }


        if (parentUuid != null) {
            Comment parent = commentRepository.findById(parentUuid)
                    .orElseThrow(() -> new IllegalArgumentException("父评论不存在: " + parentUuid));
            if (parent.getParentUuid() != null) {
                throw new IllegalArgumentException("只能回复根评论（回复请挂到根评论下，并用 replyToUuid 指定回复对象）");
            }
        }

        if (replyToUuid != null) {
            if (parentUuid == null) {
                throw new IllegalArgumentException("指定 replyToUuid 时必须同时指定 parentUuid");
            }
            if (!replyToUuid.equals(parentUuid)) {
                Comment target = commentRepository.findById(replyToUuid).orElseThrow(
                        () -> new IllegalArgumentException("回复对象不存在: " + replyToUuid)
                );
                if (!parentUuid.equals(target.getParentUuid())) {
                    throw new IllegalArgumentException("回复对象不属于当前评论");
                }
            }
        }


        long now = System.currentTimeMillis();
        Comment comment = new Comment();
        comment.setUuid(UuidUtil.generate());
        comment.setPublisherUuid(publisherUuid);
        comment.setContent(content);
        comment.setParentUuid(parentUuid);
        comment.setReplyToUuid(replyToUuid);
        comment.setScore(score);
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
    public Page<Comment> findAll(Pageable pageable) {
        return commentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Comment> findByPublisher(String publisherUuid) {
        return commentRepository.findByPublisherUuid(publisherUuid);
    }

    @Transactional(readOnly = true)
    public List<Comment> findReplies(String parentUuid) {
        return commentRepository.findByParentUuidOrderByCreateTimeAsc(parentUuid);
    }

    @Transactional(readOnly = true)
    public List<Comment> findAllByUuids(Collection<String> uuids) {
        return commentRepository.findAllById(uuids);
    }

    @Transactional
    public Comment update(String uuid, String content) {
        Comment comment = commentRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + uuid));
        PermissionGuard.requireOwnerOrAdmin(comment.getPublisherUuid());
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        comment.setContent(content);
        comment.setUpdateTime(System.currentTimeMillis());
        return commentRepository.save(comment);
    }

    @Transactional
    public void delete(String uuid) {
        Comment comment = commentRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + uuid));
        PermissionGuard.requireOwnerOrAdmin(comment.getPublisherUuid());
        if (comment.getParentUuid() != null) {
            commentRepository.findById(comment.getParentUuid()).ifPresent(parent -> {
                parent.getReplyUuids().remove(uuid);
                parent.setUpdateTime(System.currentTimeMillis());
                commentRepository.save(parent);
            });
        }
        if (comment.getParentUuid() == null) {
            commentRepository.deleteAll(commentRepository.findByParentUuidOrderByCreateTimeAsc(uuid));
        }
        commentRepository.delete(comment);
    }
}
