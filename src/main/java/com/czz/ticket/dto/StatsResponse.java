package com.czz.ticket.dto;

import java.util.List;

public record StatsResponse(
        long total,
        List<CountItem> byStatus,
        List<CountItem> byPriority,
        List<CountItem> byAssignee
) {
    public record CountItem(String key, long count) {
    }
}
