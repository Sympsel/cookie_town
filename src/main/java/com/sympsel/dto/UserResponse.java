package com.sympsel.dto;

import com.sympsel.entitys.User;
import com.sympsel.entitys.enums.Permission;

import java.util.List;

public record UserResponse(
        String uuid,
        String name,
        Permission permission,
        long createTime,
        String introduction,
        String avatar,
        List<String> tags
) {
    // 仅映射标量字段：tags 是 @ElementCollection 懒加载集合，默认置空，
    // 避免在事务外（OSIV 关闭）访问触发 LazyInitializationException。
    public static UserResponse from(User user) {
        return from(user, List.of());
    }

    // 需要标签的场景（如用户列表）：由 Service 在事务内取出 tags 后传入。
    public static UserResponse from(User user, List<String> tags) {
        return new UserResponse(
                user.getUuid(),
                user.getName(),
                user.getPermission(),
                user.getCreateTime(),
                user.getIntroduction(),
                user.getAvatar(),
                tags == null ? List.of() : tags
        );
    }
}
