package com.nextgenmanager.nextgenmanager.common.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RequiredStep {
    private int level;
    private String approverRole;
}
