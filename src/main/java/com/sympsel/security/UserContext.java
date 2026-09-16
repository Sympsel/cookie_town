package com.sympsel.security;

import com.sympsel.entitys.enums.Permission;

/**
 * 基于 ThreadLocal 的当前登录用户上下文。
 * 由 JwtAuthFilter 在解析 token 成功后写入，请求结束时清理；
 * Controller 通过 currentUuid() 获取当前操作者，替代前端明文传入的 uuid。
 */
public final class UserContext {

    public record CurrentUser(String uuid, String name, Permission permission) {
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 获取当前登录用户的 uuid；未认证时抛 UnauthorizedException（→ 401）。
     */
    public static String currentUuid() {
        return require().uuid();
    }

    /**
     * 获取当前登录用户；未认证时抛 UnauthorizedException（→ 401）。
     */
    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw new UnauthorizedException("未认证：缺少有效的登录凭证");
        }
        return user;
    }
}
