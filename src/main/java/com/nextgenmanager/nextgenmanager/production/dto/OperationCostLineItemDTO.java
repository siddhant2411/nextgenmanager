package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationCostLineItemDTO {

    private Integer sequence;
    private String operationName;
    private String workCenterName;

    private BigDecimal plannedQuantity;
    private BigDecimal completedQuantity;

    // Routing-based time (minutes)
    private BigDecimal setupTimeMinutes;
    private BigDecimal runTimePerUnitMinutes;
    private BigDecimal totalPlannedMinutes;

    // Actual time from labour entries (minutes)
    private BigDecimal actualLabourMinutes;

    // Rates
    private BigDecimal laborCostPerHour;
    private BigDecimal numberOfOperators;
    private BigDecimal machineCostPerHour;
    private BigDecimal overheadPercentage;

    // Estimated costs
    private BigDecimal estimatedLabourCost;
    private BigDecimal estimatedMachineCost;
    private BigDecimal estimatedOverheadCost;
    private BigDecimal estimatedTotalCost;

    // Actual costs
    private BigDecimal actualLabourCost;
    private BigDecimal actualMachineCost;
    private BigDecimal actualOverheadCost;
    private BigDecimal actualTotalCost;

    private BigDecimal variance;
}
