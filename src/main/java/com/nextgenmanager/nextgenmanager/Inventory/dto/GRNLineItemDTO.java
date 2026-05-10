package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GRNLineItemDTO {
    private Long id;
    private int inventoryItemId;
    private String itemCode;
    private String itemName;
    private String uom;

    private double orderedQty;
    private double receivedQty;
    private double acceptedQty;
    private double rejectedQty;

    private double rate;
    private double amount;

    private String rejectionReason;

    // ── Batch fields (for batch-tracked items) ────────────────────────────────

    /** Supplier's batch/lot number from packaging (optional input) */
    private String supplierBatchNo;

    /** Manufacturing/production date (optional input) */
    private LocalDate manufacturingDate;

    /** Expiry date from supplier label (optional input) */
    private LocalDate expiryDate;

    /** System-generated internal batch number (populated in response) */
    private String generatedBatchNumber;

    // ── Serial fields (for serial-tracked items) ──────────────────────────────

    /**
     * Optional input: list of serial numbers from manufacturer labels.
     * Size must equal acceptedQty. If null/empty → auto-generated.
     */
    private List<String> manualSerialNumbers;

    /** System-generated serial numbers (populated in response) */
    private List<String> generatedSerialNumbers;
}
