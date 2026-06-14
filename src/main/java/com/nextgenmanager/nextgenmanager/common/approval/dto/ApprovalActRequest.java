package com.nextgenmanager.nextgenmanager.common.approval.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalActRequest {
    @NotNull private Boolean approved;
    private String comment;
}
