package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;
import java.time.LocalDate;

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

    private String batchNo;
    private LocalDate expiryDate;

    private String rejectionReason;
}
