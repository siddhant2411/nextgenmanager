package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class InventoryTransactionDTO {
    private int inventoryItemId;
    private double quantity;
    private double scrappedQuantity;
    private String transactionType; // GRN, WO_ISSUE, WO_CONSUME, ADJUSTMENT, RETURN, PRODUCE
    private String referenceType;   // GRN, WORK_ORDER, SALES_ORDER
    private String referenceDocNo;
    private String warehouse;
    private double costPerUnit;
    private String createdBy;

    // For manual overrides of serial/batch tracked instances (consumption)
    private List<Long> overrideInstanceIds;
    private String overrideReason;

    // ── Batch / Serial fields (used during produceStock) ──────────────────────

    /** Supplier's batch number from packaging label (GRN only, optional) */
    private String supplierBatchNo;

    /** Manufacturing/production date for the batch (optional) */
    private LocalDate manufacturingDate;

    /** Expiry date for the batch (optional — from GRN line or item default) */
    private LocalDate expiryDate;

    /**
     * Serial numbers provided by user/supplier (GRN only, optional).
     * If null → all serials are auto-generated.
     * If provided → size must equal quantity.
     */
    private List<String> manualSerialNumbers;

    /**
     * The quality verdict to stamp on the stock this movement creates. Null means PASSED, which is
     * what an ordinary receipt is. Rejected goods arrive as FAILED, so they can be held on the
     * books without being pickable.
     */
    private com.nextgenmanager.nextgenmanager.Inventory.model.QualityStatus qualityStatus;
}
