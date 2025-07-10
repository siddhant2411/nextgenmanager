package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;

import java.util.List;

public class GroupedInventoryItem {
    private InventoryItem inventoryItem;
    private List<InventoryInstance> inventoryInstances;

    // Constructors
    public GroupedInventoryItem() {}

    public GroupedInventoryItem(InventoryItem item, List<InventoryInstance> instances) {
        this.inventoryItem = item;
        this.inventoryInstances = instances;
    }

    // Getters and setters
    public InventoryItem getInventoryItem() {
        return inventoryItem;
    }

    public void setInventoryItem(InventoryItem inventoryItem) {
        this.inventoryItem = inventoryItem;
    }

    public List<InventoryInstance> getInventoryInstances() {
        return inventoryInstances;
    }

    public void setInventoryInstances(List<InventoryInstance> inventoryInstances) {
        this.inventoryInstances = inventoryInstances;
    }
}
