package com.sympsel.security;

/**
 * @brief 在权限不足时抛出
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
