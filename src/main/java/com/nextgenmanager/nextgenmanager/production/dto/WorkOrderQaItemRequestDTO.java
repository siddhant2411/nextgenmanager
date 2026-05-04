package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkOrderQaItemRequestDTO {
    private Long entryId;
    private BigDecimal actualValue;
    private Boolean passed;
    private String remarks;
}
