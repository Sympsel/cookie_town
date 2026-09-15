package com.sympsel.dto;

public record TownRequest(String name, String ownerUuid, String description, String parentTownUuid) {
}
