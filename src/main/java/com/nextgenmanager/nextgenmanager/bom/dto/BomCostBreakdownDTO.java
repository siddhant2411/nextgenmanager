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

    // Totals
    private BigDecimal totalCost;            // material + operation
}
