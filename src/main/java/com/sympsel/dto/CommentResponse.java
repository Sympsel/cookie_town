package com.sympsel.dto;

import com.sympsel.entitys.Comment;

public record CommentResponse(
        String uuid,
        String publisherUuid,
        String parentUuid,
        String content,
        long createTime,
        long updateTime
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getUuid(),
                comment.getPublisherUuid(),
                comment.getParentUuid(),
                comment.getContent(),
                comment.getCreateTime(),
                comment.getUpdateTime()
        );
    }
}
