package com.sympsel.dto;

import com.sympsel.entitys.Landmark;
import com.sympsel.entitys.enums.LandmarkStatus;
import com.sympsel.entitys.enums.LandmarkType;

import java.util.List;

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
        long updateTime,
        List<String> builderNames
) {
    public static LandmarkResponse from(Landmark l) { return from(l, null, List.of()); }

    public static LandmarkResponse from(Landmark l, String submitterName) {
        return from(l, submitterName, List.of());
    }

    public static LandmarkResponse from(Landmark l, String submitterName, List<String> builderNames) {
        return new LandmarkResponse(
                l.getUuid(), l.getSubmitterUuid(), submitterName,
                l.getParentUuid(), l.getName(), l.getDescription(), l.getType(), l.getStatus(),
                l.getScore(), l.getCreateTime(), l.getUpdateTime(),
                builderNames == null ? List.of() : builderNames);
    }
}
