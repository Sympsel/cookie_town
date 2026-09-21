package com.sympsel.dto;

import com.sympsel.entitys.Comment;
import com.sympsel.entitys.enums.Score;

public record CommentResponse(
        String uuid,
        String publisherUuid,
        String publisherName,
        String parentUuid,
        String replyToUuid,
        String replyToName,
        String content,
        Score score,
        long createTime,
        long updateTime
) {

    public static CommentResponse from(Comment c) { return from(c, null, null); }

    public static CommentResponse from(Comment comment, String publisherName, String replyToName) {
        return new CommentResponse(
                comment.getUuid(),
                comment.getPublisherUuid(),
                publisherName,
                comment.getParentUuid(),
                comment.getReplyToUuid(),
                replyToName,
                comment.getContent(),
                comment.getScore(),
                comment.getCreateTime(),
                comment.getUpdateTime()
        );
    }
}
