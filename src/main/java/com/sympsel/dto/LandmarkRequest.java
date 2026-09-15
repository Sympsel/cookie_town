package com.sympsel.dto;

import com.sympsel.entitys.enums.LandmarkType;

public record LandmarkRequest(String submitterUuid, String name, LandmarkType type, String description) {
}
