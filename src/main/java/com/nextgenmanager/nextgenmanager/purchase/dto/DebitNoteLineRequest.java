package com.nextgenmanager.nextgenmanager.purchase.dto;

import lombok.Data;

@Data
public class DebitNoteLineRequest {
    private int inventoryItemId;
    private int lineNumber;
    private double returnedQty;
    private double rate;
    private double gstRate;       // e.g. 18.0
    private String warehouseFrom;
    private String remarks;
}
