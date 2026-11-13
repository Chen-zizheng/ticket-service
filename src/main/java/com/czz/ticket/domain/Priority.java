package com.czz.ticket.domain;

import java.util.Arrays;

public enum Priority {

    LOW, MEDIUM, HIGH, URGENT;

    public static Priority parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        String upper = raw.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(p -> p.name().equals(upper))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知优先级: " + raw));
    }
}
