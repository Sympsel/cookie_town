package com.sympsel.dto;

import com.sympsel.entitys.Town;

public record TownResponse(
        String uuid,
        String name,
        String ownerUuid,
        String parentTownUuid,
        String description,
        double score,
        long createTime,
        long updateTime
) {
    public static TownResponse from(Town town) {
        return new TownResponse(
                town.getUuid(),
                town.getName(),
                town.getOwnerUuid(),
                town.getParentTownUuid(),
                town.getDescription(),
                town.getScore(),
                town.getCreateTime(),
                town.getUpdateTime()
        );
    }
}
