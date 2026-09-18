package com.sympsel.dto;

import com.sympsel.entitys.Town;

public record TownResponse(
        String uuid,
        String name,
        String ownerUuid,
        String ownerName,
        String parentTownUuid,
        String description,
        double score,
        long createTime,
        long updateTime
) {
    public static TownResponse from(Town town) {
        return from(town, null);
    }

    /**
     * @param ownerName 镇长用户名（可为 null，表示未知或无主）
     */
    public static TownResponse from(Town town, String ownerName) {
        return new TownResponse(
                town.getUuid(),
                town.getName(),
                town.getOwnerUuid(),
                ownerName,
                town.getParentTownUuid(),
                town.getDescription(),
                town.getScore(),
                town.getCreateTime(),
                town.getUpdateTime()
        );
    }
}