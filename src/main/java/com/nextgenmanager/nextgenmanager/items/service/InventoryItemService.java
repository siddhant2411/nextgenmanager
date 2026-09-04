package com.nextgenmanager.nextgenmanager.items.service;


import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.DTO.InventorySettingsBackfillDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public interface InventoryItemService {

    public com.nextgenmanager.nextgenmanager.items.model.InventoryItem addInventoryItem(com.nextgenmanager.nextgenmanager.items.model.InventoryItem inventoryItem);

    /**
     * Gives every active item that has none a default set of inventory settings, so that stock
     * can be held, reserved and picked against it at all.
     *
     * <p>An item is classified as purchased when it has ever been on a purchase order, and as
     * manufactured otherwise. Nothing else is invented: reorder levels, stock limits and lead
     * times are left at zero, which reads as "no policy set" rather than as a policy nobody
     * chose. Items that already have settings are never touched.
     *
     * @param dryRun when true, reports what it would write and writes nothing
     */
    InventorySettingsBackfillDto backfillInventorySettings(boolean dryRun);

    @Transactional(propagation = Propagation.REQUIRED)
    public com.nextgenmanager.nextgenmanager.items.model.InventoryItem getInventoryItem(int itemId);

    public Page<InventoryItemDTO> getAllInventoryItems(int page, int size, String sortBy, String sortDir, String query);

    public java.util.List<com.nextgenmanager.nextgenmanager.items.model.InventoryItem> getAllInventoryItemsWithDeleted();

    public void deleteInventoryItem(int itemId);

    public void deleteInventoryItemDb(int itemId);


    public void removeDeletedInventoryItemDb();

    public com.nextgenmanager.nextgenmanager.items.model.InventoryItem editInventoryItem(int itemId, com.nextgenmanager.nextgenmanager.items.model.InventoryItem updatedItem);

    public Page<com.nextgenmanager.nextgenmanager.items.model.InventoryItem> searchInventoryItems(String query,int page, int size);

    public String generateUniqueCode();

    public boolean checkItemCodeExists(String itemCode);

    public Page<InventoryItemDTO> filterInventoryItems(com.nextgenmanager.nextgenmanager.common.dto.FilterRequest request);



}
