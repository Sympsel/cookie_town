package com.sympsel.dto;

/**
 * 重置密码请求体：仅本人或拥有特权标签的开发者可提交（Service 层校验）。
 */
public record PasswordResetRequest(String password) {
}
