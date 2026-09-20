package com.sympsel.dto;

import com.sympsel.entitys.MessageBoard;
import com.sympsel.entitys.enums.Score;

public record MessageBoardResponse(
        String uuid,
        String publisherUuid,
        String publisherName,
        String content,
        Score score,
        long createTime,
        long updateTime
) {
    public static MessageBoardResponse from(MessageBoard messageBoard) {
        return MessageBoardResponse.from(messageBoard, null);
    }

    public static MessageBoardResponse from(MessageBoard messageBoard, String publisherName) {
        return new MessageBoardResponse(
                messageBoard.getUuid(),
                messageBoard.getPublisherUuid(),
                messageBoard.getContent(),
                publisherName,
                messageBoard.getScore(),
                messageBoard.getCreateTime(),
                messageBoard.getUpdateTime()
        );
    }
}
