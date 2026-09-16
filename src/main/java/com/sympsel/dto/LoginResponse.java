package com.sympsel.dto;

/**
 * 登录成功响应：返回 JWT token、token 类型、有效期（毫秒）及当前用户公开信息。
 */
public record LoginResponse(String token, String tokenType, long expiresInMillis, UserResponse user) {
}
