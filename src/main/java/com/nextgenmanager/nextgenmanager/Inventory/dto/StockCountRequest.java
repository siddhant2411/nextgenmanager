package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.Data;

import java.util.List;

@Data
public class StockCountRequest {
    private List<StockCountLine> lines;
    private String warehouse;    // optional — null means all warehouses
    private String countedBy;
    private String remarks;
}
