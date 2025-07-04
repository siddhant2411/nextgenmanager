package com.nextgenmanager.nextgenmanager.Inventory.service;


import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryPresentDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import io.swagger.models.auth.In;
import org.springframework.data.domain.Page;

import java.util.List;

public interface InventoryInstanceService  {

    public List<InventoryInstance> getAllInventoryInstances(int page, int size, String sortBy, String sortDir, String query);

    public Page<InventoryPresentDTO> getPresentInventoryInstances(int page, int size, String sortBy, String sortDir, String queryItemCode,
                                                                  String queryItemName, String queryHsnCode, Double totalQuantityCondition, String filterType, UOM queryUOM, ItemType itemType);

    public List<InventoryInstance> getInventoryInstanceByItemId(int inventoryItemId,int page, int size, String sortBy, String sortDir, String query);

    public List<InventoryInstance> createInstances(InventoryItem item, double qty, InventoryInstance template);

    public List<InventoryInstance> consumeInventoryInstance(InventoryItem inventoryItem, double consumedQty);

    public void consumeInventoryInstance(List<InventoryInstance> instances);

    public List<InventoryInstance> bookInventoryInstance(InventoryItem inventoryItem, double bookedQty);

    public List<InventoryInstance> requestInstance(InventoryItem inventoryItem, double requestedQty);

    public InventoryInstance updateInventoryInstance(InventoryInstance inventoryInstance);

    public void deleteInventoryInstance(long id);

    public InventoryInstance getInventoryInstanceById(long id);

    public void updateItemAvailability(int itemId);

    public void revertInventoryInstances(List<InventoryInstance> instances);
}
