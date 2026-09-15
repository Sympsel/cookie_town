package com.sympsel.utils;

import java.util.UUID;

public class UuidUtil {
    private UuidUtil() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String uuid) {
        if (uuid == null || uuid.length() != 36) return false;
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
