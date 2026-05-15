package com.nextgenmanager.nextgenmanager.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderProfitabilityDto {
    private Long salesOrderId;
    private BigDecimal totalRevenue;
    private BigDecimal totalActualCost;
    private BigDecimal grossProfit;
    private BigDecimal grossMarginPercentage;
    private Map<Integer, BigDecimal> itemCosts;
}
