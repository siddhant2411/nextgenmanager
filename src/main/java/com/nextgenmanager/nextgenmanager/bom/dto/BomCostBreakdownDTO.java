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

    // Overhead — see OverheadPolicy. BLANKET: the BOM's own % on material + in-house conversion +
    // additional + fixed-rate operations, EXCLUDING subcontracted operations. WORK_CENTER: the BOM %
    // is blank, so each operation line carries its work center's overhead instead.
    private String overheadMode;              // "BLANKET" | "WORK_CENTER"
    private BigDecimal overheadPercentage;    // the BOM's blanket rate (null in WORK_CENTER mode)
    private BigDecimal overheadBase;          // the cost base the blanket % is applied to
    private BigDecimal overheadCost;          // blanket only: overheadBase × overheadPercentage / 100
    private BigDecimal workCenterOverheadCost; // sum of the operation lines' overhead; already inside totalOperationCost

    // Totals
    private BigDecimal totalCost;            // material + operation + additional + overhead
}
