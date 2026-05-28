package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;

@Data
public class StockCountLine {
    private int inventoryItemId;
    private double physicalQty;   // what the user actually counted
    private String remarks;        // per-line note, e.g. "shelf 3B, damaged goods"
}
