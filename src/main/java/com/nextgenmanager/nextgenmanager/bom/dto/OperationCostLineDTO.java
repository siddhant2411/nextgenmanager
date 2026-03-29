package com.nextgenmanager.nextgenmanager.bom.dto;

import com.nextgenmanager.nextgenmanager.production.enums.CostType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationCostLineDTO {
    private Long operationId;
    private Integer sequenceNumber;
    private String operationName;
    private CostType costType;

    // Work center info
    private String workCenterName;
    private BigDecimal overheadPercentage;

    // Machine info
    private String machineName;
    private BigDecimal machineCostRate;      // machine-specific or workCenter rate

    // Labor info
    private String laborRoleName;
    private BigDecimal laborCostRate;        // laborRole.costPerHour
    private Integer numberOfOperators;

    // Time
    private BigDecimal setupTime;
    private BigDecimal runTime;
    private BigDecimal totalTime;            // setup + run

    // Cost breakdown (only for CALCULATED type)
    private BigDecimal machineCost;          // machineCostRate × totalTime
    private BigDecimal laborCost;            // laborCostRate × numberOfOperators × totalTime
    private BigDecimal subtotal;             // machineCost + laborCost
    private BigDecimal overheadCost;         // subtotal × overhead%/100
    private BigDecimal totalCost;            // subtotal + overheadCost (or fixedCostPerUnit)
}
