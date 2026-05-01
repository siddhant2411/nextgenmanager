package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostOfProductionDTO {

    private String workOrderNumber;
    private String itemName;
    private String itemCode;

    private BigDecimal plannedQuantity;
    private BigDecimal completedQuantity;

    // ── Estimated ────────────────────────────────────────────────────────────

    private BigDecimal estimatedMaterialCost;
    private BigDecimal estimatedLabourCost;
    private BigDecimal estimatedMachineCost;
    private BigDecimal estimatedOverheadCost;
    private BigDecimal totalEstimatedCost;
    private BigDecimal estimatedCostPerUnit;

    // ── Actual ───────────────────────────────────────────────────────────────

    private BigDecimal actualMaterialCost;
    private BigDecimal actualLabourCost;
    private BigDecimal actualMachineCost;
    private BigDecimal actualOverheadCost;
    private BigDecimal totalActualCost;
    private BigDecimal actualCostPerUnit;

    // ── Variance (actual − estimated) ────────────────────────────────────────

    private BigDecimal materialVariance;
    private BigDecimal labourVariance;
    private BigDecimal machineVariance;
    private BigDecimal overheadVariance;
    private BigDecimal totalVariance;
    private BigDecimal totalVariancePercentage;

    // ── Line items ───────────────────────────────────────────────────────────

    private List<MaterialCostLineItemDTO> materials;
    private List<OperationCostLineItemDTO> operations;
}
