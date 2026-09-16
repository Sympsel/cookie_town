package com.sympsel.dto;

import com.sympsel.entitys.enums.LandmarkType;

public record LandmarkRequest(String name, LandmarkType type, String description) {
}
