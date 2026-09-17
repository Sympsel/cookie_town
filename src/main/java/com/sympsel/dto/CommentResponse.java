package com.sympsel.dto;

import com.sympsel.entitys.Comment;
import com.sympsel.entitys.enums.Score;

public record CommentResponse(
        String uuid,
        String publisherUuid,
        String parentUuid,
        String content,
        Score score,
        long createTime,
        long updateTime
) {
    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getUuid(),
                comment.getPublisherUuid(),
                comment.getParentUuid(),
                comment.getContent(),
                comment.getScore(),
                comment.getCreateTime(),
                comment.getUpdateTime()
        );
    }
}
