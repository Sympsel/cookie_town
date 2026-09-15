package com.sympsel.dto;

import com.sympsel.entitys.enums.Score;

public record MessageBoardRequest(String publisherUuid, String content, Score score) {
}
