package com.sympsel.security;

import com.sympsel.entitys.enums.Permission;

import java.util.Objects;

public final class PermissionGuard {
    private PermissionGuard() {
    }

    public static void requireOwnerOrAdmin(String ownerUuid) {
        UserContext.CurrentUser currentUser = UserContext.require();
        if (currentUser.permission() == Permission.Admin) {
            return;
        }
        if (!Objects.equals(currentUser.uuid(), ownerUuid)) {
            throw new ForbiddenException("无权操作：只能修改或删除自己创建的内容");
        }
    }
}
