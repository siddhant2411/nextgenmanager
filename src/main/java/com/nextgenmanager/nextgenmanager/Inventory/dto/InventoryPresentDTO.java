package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InventoryPresentDTO {

    private int inventoryItemId;

    private String itemCode;

    private String name;

    private String hsnCode;

    private ItemType itemType;

    private UOM uom;

    private double totalQuantity;

    private double averageCost;
}
