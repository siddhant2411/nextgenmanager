package com.nextgenmanager.nextgenmanager.items.service;


import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import org.springframework.data.domain.Page;

import java.util.List;

public interface InventoryItemService {

    public InventoryItem addInventoryItem(InventoryItem inventoryItem);

    public InventoryItem getInventoryItem(int itemId);

    public Page<InventoryItem> getAllInventoryItems(int page, int size, String sortBy, String sortDir, String query);

    public List<InventoryItem> getAllInventoryItemsWithDeleted();

    public void deleteInventoryItem(int itemId);

    public void deleteInventoryItemDb(int itemId);


    public void removeDeletedInventoryItemDb();

    public InventoryItem editInventoryItem(int itemId, InventoryItem updatedItem);

    public Page<InventoryItem> searchInventoryItems(String query,int page, int size);


}
