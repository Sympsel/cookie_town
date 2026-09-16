package com.sympsel.dto;

import com.sympsel.entitys.enums.Score;

public record MessageBoardRequest(String content, Score score) {
}
