package com.czz.ticket.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/** 工单状态机：非法流转一律拒绝，避免"已关闭的工单被重新派单"这类脏数据。 */
public enum TicketStatus {

    OPEN("待处理"),
    ASSIGNED("已派单"),
    IN_PROGRESS("处理中"),
    RESOLVED("已解决"),
    CLOSED("已关闭");

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            OPEN, Set.of(ASSIGNED, IN_PROGRESS, CLOSED),
            ASSIGNED, Set.of(IN_PROGRESS, OPEN, CLOSED),
            IN_PROGRESS, Set.of(RESOLVED, ASSIGNED, CLOSED),
            RESOLVED, Set.of(CLOSED, IN_PROGRESS),
            CLOSED, Set.of()
    );

    public final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public boolean canMoveTo(TicketStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public static TicketStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("status 不能为空");
        }
        String upper = raw.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(s -> s.name().equals(upper))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知状态: " + raw));
    }
}
