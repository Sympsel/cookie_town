package com.sympsel.dto;

import com.sympsel.entitys.Notice;

public record NoticeResponse(
        String uuid,
        String publisherUuid,
        String publisherName,
        String title,
        String content,
        long publishTime,
        long updateTime
) {
    public static NoticeResponse from(Notice notice, String publisherName) {
        return new NoticeResponse(
                notice.getUuid(),
                notice.getPublisherUuid(),
                publisherName,
                notice.getTitle(),
                notice.getContent(),
                notice.getPublishTime(),
                notice.getUpdateTime()
        );
    }
}
