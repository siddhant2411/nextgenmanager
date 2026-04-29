package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;
import java.util.List;

@Data
public class InventoryTransactionDTO {
    private int inventoryItemId;
    private double quantity;
    private double scrappedQuantity;
    private String transactionType; // GRN, WO_ISSUE, WO_CONSUME, ADJUSTMENT, RETURN
    private String referenceType;   // GRN, WORK_ORDER, SALES_ORDER
    private String referenceDocNo;
    private String warehouse;
    private double costPerUnit;
    private String createdBy;

    // For manual overrides of serial/batch tracked instances
    private List<Long> overrideInstanceIds;
    private String overrideReason;
}
