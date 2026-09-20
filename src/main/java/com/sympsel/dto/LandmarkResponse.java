package com.sympsel.dto;

import com.sympsel.entitys.Landmark;
import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.LandmarkType;

public record LandmarkResponse(
        String uuid,
        String submitterUuid,
        String submitterName,
        String parentUuid,
        String name,
        String description,
        LandmarkType type,
        LandmarkStatus status,
        double score,
        long createTime,
        long updateTime
) {
    public static LandmarkResponse from(Landmark landmark, String submitterName) {
        return new LandmarkResponse(
                landmark.getUuid(),
                landmark.getSubmitterUuid(),
                submitterName,
                landmark.getParentUuid(),
                landmark.getName(),
                landmark.getDescription(),
                landmark.getType(),
                landmark.getStatus(),
                landmark.getScore(),
                landmark.getCreateTime(),
                landmark.getUpdateTime()
        );
    }
}
