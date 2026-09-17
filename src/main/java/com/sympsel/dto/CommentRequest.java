package com.sympsel.dto;

import com.sympsel.entitys.enums.Score;

// score 可空：评论不一定携带评分（不评分时前端不提交该字段）
public record CommentRequest(String content, String parentUuid, Score score) {
}
