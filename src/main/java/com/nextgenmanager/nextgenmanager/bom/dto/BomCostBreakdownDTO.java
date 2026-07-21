package com.nextgenmanager.nextgenmanager.bom.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BomCostBreakdownDTO {
    private int bomId;
    private String bomName;
    private String parentItemCode;
    private String parentItemName;

    // Material costs
    private List<MaterialCostLineDTO> materialCosts;
    private BigDecimal totalMaterialCost;

    // Operation costs
    private List<OperationCostLineDTO> operationCosts;
    private BigDecimal totalOperationCost;

    // Additional / consumable flat costs (grease, cleaning, packing, …)
    private List<BomCostLineDTO> additionalCosts;
    private BigDecimal totalAdditionalCost;

    // Blanket manufacturing overhead (per-BOM %), applied to material + in-house conversion +
    // additional + fixed-rate operations, EXCLUDING subcontracted operations.
    private BigDecimal overheadPercentage;   // the BOM's blanket rate (null/0 = none)
    private BigDecimal overheadBase;          // the cost base the % is applied to
    private BigDecimal overheadCost;          // overheadBase × overheadPercentage / 100

    // Totals
    private BigDecimal totalCost;            // material + operation + additional + overhead
}
