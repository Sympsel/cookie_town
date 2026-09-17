package com.sympsel.dto;

import com.sympsel.entitys.enums.LandmarkType;

import java.util.List;

public record LandmarkRequest(String name, LandmarkType type, String description, List<String> pictures) {
}
