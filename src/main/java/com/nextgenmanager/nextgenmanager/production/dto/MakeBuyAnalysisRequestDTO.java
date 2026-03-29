package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * Input for a Make-or-Buy analysis.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MakeBuyAnalysisRequestDTO {

    /** Inventory item to analyse (required). */
    private Integer itemId;

    /**
     * BOM to use for MAKE cost calculation.
     * If null, the system picks the active BOM for the item automatically.
     */
    private Integer bomId;

    /** Batch quantity to produce/purchase. Defaults to 1 if null. */
    private BigDecimal quantity;

    /**
     * Override purchase price per unit.
     * If null, falls back to item's lastPurchaseCost → standardCost.
     */
    private BigDecimal buyPricePerUnitOverride;

    /**
     * Override job-work (subcontract) rate per unit charged by the vendor.
     * If null, the rate is read from routing operations whose CostType = SUB_CONTRACTED.
     */
    private BigDecimal subcontractRatePerUnitOverride;
}
