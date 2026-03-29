package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.bom.dto.MaterialCostLineDTO;
import com.nextgenmanager.nextgenmanager.bom.dto.OperationCostLineDTO;
import com.nextgenmanager.nextgenmanager.production.enums.MakeBuyDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of a Make-or-Buy analysis for an item at a given batch quantity.
 *
 * Three strategies are compared:
 *   MAKE        — manufacture in-house using BOM + Routing
 *   BUY         — purchase finished good from supplier
 *   SUBCONTRACT — supply raw materials to a job-work vendor (GST Section 143)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MakeBuyAnalysisDTO {

    private Integer itemId;
    private String itemCode;
    private String itemName;

    /** Batch quantity used for the analysis. */
    private BigDecimal quantity;

    private MakeAnalysis makeAnalysis;
    private BuyAnalysis buyAnalysis;
    private SubcontractAnalysis subcontractAnalysis;

    /** System recommendation. */
    private MakeBuyDecision recommendation;

    /** Plain-language explanation of the recommendation (Indian MSME context). */
    private String recommendationReason;

    /**
     * Cost difference: (makeUnitCost - buyUnitCost) / buyUnitCost × 100.
     * Positive = making is more expensive; negative = making is cheaper.
     * Null when either make or buy data is unavailable.
     */
    private BigDecimal makeBuyCostDifferencePct;

    /**
     * The batch quantity at which in-house manufacturing breaks even with buying.
     * Below this quantity, buying is cheaper (setup cost not yet amortized).
     * Null when calculation is not possible.
     */
    private BigDecimal breakEvenQuantity;

    // ──────────────────────────── inner analyses ────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MakeAnalysis {

        /** False when no active BOM exists for this item. */
        private boolean available;

        private Integer bomId;
        private Long routingId;

        /** Per-unit material cost (BOM components × standard cost). */
        private BigDecimal unitMaterialCost;

        /** unitMaterialCost × quantity. */
        private BigDecimal batchMaterialCost;

        /**
         * Setup cost amortized per unit (one-time setup divided by batch size).
         * Encourages larger production runs.
         */
        private BigDecimal unitSetupCost;

        /** Per-unit run cost (variable cost, scales linearly with quantity). */
        private BigDecimal unitRunCost;

        /** unitSetupCost + unitRunCost. */
        private BigDecimal unitOperationCost;

        /** unitOperationCost × quantity. */
        private BigDecimal batchOperationCost;

        /** unitMaterialCost + unitOperationCost. */
        private BigDecimal unitTotalCost;

        /** batchMaterialCost + batchOperationCost. */
        private BigDecimal batchTotalCost;

        private List<MaterialCostLineDTO> materialLines;
        private List<OperationCostLineDTO> operationLines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuyAnalysis {

        /** False when no purchase price data is available. */
        private boolean available;

        private BigDecimal unitCost;
        private BigDecimal batchTotalCost;

        /**
         * Source of the unit cost:
         * VENDOR_PRICE | LAST_PURCHASE | STANDARD_COST | MANUAL_OVERRIDE
         */
        private String priceSource;

        /** Preferred / cheapest vendor details (null when source is not VENDOR_PRICE). */
        private Integer vendorId;
        private String vendorName;

        /**
         * Whether the vendor is GST-registered.
         * GST-registered suppliers allow ITC recovery, lowering effective buy cost.
         */
        private Boolean gstRegistered;

        /** Supplier lead time in days. */
        private Double leadTimeDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubcontractAnalysis {

        /**
         * True when an ItemVendorPrice with priceType=JOB_WORK exists,
         * or a subcontractRatePerUnitOverride was provided in the request.
         */
        private boolean available;

        /** Same as MAKE material cost — you supply the raw materials. */
        private BigDecimal unitMaterialCost;

        /** Job-work charge per unit paid to the vendor. */
        private BigDecimal unitJobWorkCost;

        /** unitMaterialCost + unitJobWorkCost. */
        private BigDecimal unitTotalCost;

        /** unitTotalCost × quantity. */
        private BigDecimal batchTotalCost;

        /** Job-work vendor details (null when source is MANUAL_OVERRIDE). */
        private Integer vendorId;
        private String vendorName;
        private Boolean gstRegistered;
    }
}
