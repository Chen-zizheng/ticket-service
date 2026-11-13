package com.czz.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 120, message = "标题不能超过 120 字")
        String title,

        @Size(max = 2000, message = "描述不能超过 2000 字")
        String description,

        String priority
) {
}
