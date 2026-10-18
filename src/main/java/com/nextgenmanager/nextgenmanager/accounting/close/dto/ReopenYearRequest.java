package com.nextgenmanager.nextgenmanager.accounting.close.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReopenYearRequest {
    @NotBlank(message = "Give a reason for reopening the year")
    @Size(max = 500)
    private String reason;
}
