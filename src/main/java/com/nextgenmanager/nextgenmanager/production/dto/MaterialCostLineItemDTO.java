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
public class MaterialCostLineItemDTO {

    private String itemCode;
    private String itemName;

    private BigDecimal standardCost;

    private BigDecimal plannedQuantity;
    private BigDecimal consumedQuantity;

    private BigDecimal estimatedCost;
    private BigDecimal actualCost;
    private BigDecimal variance;
}
