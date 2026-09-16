package com.sympsel.security;

/**
 * 认证失败异常：缺少有效凭证、token 无效/过期、或登录凭据错误时抛出。
 * 由 GlobalExceptionHandler 统一转换为 HTTP 401 + {"error": "..."} 响应。
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
