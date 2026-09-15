package com.sympsel.dto;

import com.sympsel.entitys.Notice;

public record NoticeResponse(
        String uuid,
        String publisherUuid,
        String title,
        String content,
        long publishTime,
        long updateTime
) {
    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                notice.getUuid(),
                notice.getPublisherUuid(),
                notice.getTitle(),
                notice.getContent(),
                notice.getPublishTime(),
                notice.getUpdateTime()
        );
    }
}
