package com.nextgenmanager.nextgenmanager.items.service;


import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public interface InventoryItemService {

    public InventoryItem addInventoryItem(InventoryItem inventoryItem);

    @Transactional(propagation = Propagation.REQUIRED)
    public InventoryItem getInventoryItem(int itemId);

    public Page<InventoryItemDTO> getAllInventoryItems(int page, int size, String sortBy, String sortDir, String query);

    public List<InventoryItem> getAllInventoryItemsWithDeleted();

    public void deleteInventoryItem(int itemId);

    public void deleteInventoryItemDb(int itemId);


    public void removeDeletedInventoryItemDb();

    public InventoryItem editInventoryItem(int itemId, InventoryItem updatedItem);

    public Page<InventoryItem> searchInventoryItems(String query,int page, int size);

    public String generateUniqueCode();

    public Page<InventoryItemDTO> filterInventoryItems(FilterRequest request);



}
