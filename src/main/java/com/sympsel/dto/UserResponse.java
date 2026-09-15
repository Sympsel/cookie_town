package com.sympsel.dto;

import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;

public record UserResponse(
        String uuid,
        String name,
        Permission permission,
        long createTime,
        String introduction
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUuid(),
                user.getName(),
                user.getPermission(),
                user.getCreateTime(),
                user.getIntroduction()
        );
    }
}