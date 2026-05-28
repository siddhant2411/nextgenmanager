package com.nextgenmanager.nextgenmanager.Inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockValuationLineDTO {
    private int    inventoryItemId;
    private String itemCode;
    private String itemName;
    private String itemType;       // RAW_MATERIAL, FINISHED_GOODS, SEMI_FINISHED, CONSUMABLE
    private String uom;
    private double closingQty;
    private double avgRate;        // lastPurchaseCost or standardCost, whichever is available
    private double totalValue;     // closingQty * avgRate
    private String hsnCode;
}
