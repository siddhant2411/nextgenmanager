package com.nextgenmanager.nextgenmanager.bom.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialCostLineDTO {
    private int positionId;
    private String itemCode;
    private String itemName;
    private String uom;
    private double quantity;
    private BigDecimal scrapPercentage;
    private BigDecimal effectiveQuantity;   // quantity × (1 + scrap%/100)
    private BigDecimal unitCost;            // from standardCost
    private BigDecimal totalCost;           // effectiveQuantity × unitCost

    // Which routing operation this material is consumed at (null = WO level)
    private Long routingOperationId;
    private String routingOperationName;
}
