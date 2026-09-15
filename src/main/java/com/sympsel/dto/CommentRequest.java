package com.sympsel.dto;

public record CommentRequest(String publisherUuid, String content, String parentUuid) {
}
