package com.czz.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusChangeRequest(@NotBlank(message = "status 不能为空") String status) {
}
