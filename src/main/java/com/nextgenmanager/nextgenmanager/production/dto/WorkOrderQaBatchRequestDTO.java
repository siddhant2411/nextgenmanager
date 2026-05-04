package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WorkOrderQaBatchRequestDTO {
    private BigDecimal checkedQuantity;
    private String checkedBy;
    private List<WorkOrderQaItemRequestDTO> items;
}
