package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YieldMetricsDTO {

    private int workOrderId;
    private String workOrderNumber;
    private BigDecimal plannedQuantity;
    private BigDecimal totalGoodQuantity;
    private BigDecimal totalRejectedQuantity;
    private BigDecimal totalScrapQuantity;
    /** Good units / Planned × 100 */
    private BigDecimal firstPassYield;
    /** Rejected units / Planned × 100 */
    private BigDecimal reworkRate;
    /** Scrap units / Planned × 100 */
    private BigDecimal scrapRate;
    /** (Good + Rejected) / Planned × 100 — includes reworkable units */
    private BigDecimal overallYield;
}
