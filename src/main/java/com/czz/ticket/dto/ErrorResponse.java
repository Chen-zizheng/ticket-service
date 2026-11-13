package com.czz.ticket.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<String> details,
        LocalDateTime timestamp
) {
}
