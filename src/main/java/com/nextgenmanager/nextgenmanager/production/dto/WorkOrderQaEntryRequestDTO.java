package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkOrderQaEntryRequestDTO {
    private BigDecimal actualValue;
    private Boolean passed;
    private String remarks;
    private String checkedBy;
}
