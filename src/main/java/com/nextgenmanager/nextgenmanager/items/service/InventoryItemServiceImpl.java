package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemCode;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.production.repository.ItemCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Date;
import java.util.List;

@Service
public class InventoryItemServiceImpl implements InventoryItemService{

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ItemCodeRepository itemCodeRepository;

    private static final Logger logger = LoggerFactory.getLogger(InventoryItem.class);

    @Override
    public InventoryItem addInventoryItem(InventoryItem inventoryItem) {

        logger.debug("Adding inventory item: {}", inventoryItem.toString());
        try {
            InventoryItem savedInventoryItem = inventoryItemRepository.save(inventoryItem);
            logger.info("Item Successfully added with inventory item id: {}",savedInventoryItem.getInventoryItemId());
            return savedInventoryItem;
        }
        catch (Exception e){
            logger.error("Error while adding new inventory item: {} ",e.getMessage());
            throw new RuntimeException(e);
        }

    }

    @Override
    public InventoryItem getInventoryItem(int itemId) {
        logger.debug("Fetching data for id: {}",itemId);
        try{
            InventoryItem inventoryItem = inventoryItemRepository.findByActiveId(itemId);
            if(inventoryItem == null){
                logger.warn("No inventory item with id: {}",itemId);
                throw new IllegalAccessException("Item does not exist");
            }
            return inventoryItem;
        }catch (Exception e){
            logger.error("Error fetching inventory item with id: {}",itemId);
            throw new RuntimeException(e);
        }
    }
    @Override
    public Page<InventoryItem> getAllInventoryItems(int page, int size, String sortBy, String sortDir,String query) {
        logger.debug("Fetching all active inventory items with pagination and sorting");
        try {
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size,sort);
            Page<InventoryItem> activeItems = inventoryItemRepository.findAllActiveCategory(query.toLowerCase(),pageable);
            if (activeItems.isEmpty()) {
                logger.warn("No active inventory items found");
            } else {
                logger.info("Fetched {} active inventory items", activeItems.getTotalElements());
            }
            return activeItems;
        } catch (Exception e) {
            logger.error("Error while fetching all active inventory items: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<InventoryItem> getAllInventoryItemsWithDeleted() {
        logger.debug("Fetching all inventory items including deleted");
        try {
            List<InventoryItem> allItems = inventoryItemRepository.findAll();
            if (allItems.isEmpty()) {
                logger.warn("No inventory items found");
            } else {
                logger.info("Fetched {} inventory items including deleted", allItems.size());
            }
            return allItems;
        } catch (Exception e) {
            logger.error("Error while fetching all inventory items with deleted: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteInventoryItem(int itemId) {
        logger.debug("Deleting inventory item with id: {}", itemId);
        try {
            InventoryItem inventoryItem = inventoryItemRepository.findById(itemId)
                    .orElseThrow(() -> {
                        logger.warn("No inventory item found with id: {}", itemId);
                        return new IllegalArgumentException("Item does not exist");
                    });

            inventoryItem.setDeletedDate(new Date());
            inventoryItemRepository.save(inventoryItem);

            logger.info("Inventory item with id: {} successfully marked as deleted", itemId);
        } catch (Exception e) {
            logger.error("Error while deleting inventory item with id: {}: {}", itemId, e.getMessage());
            throw new RuntimeException(e);
        }
    }
//    TODO: remove from the DB
    @Override
    public void deleteInventoryItemDb(int itemId) {

    }

    // TODO:   remove all the data which has deleted data
    @Override
    public void removeDeletedInventoryItemDb() {

    }

    @Override
    public InventoryItem editInventoryItem(int itemId, InventoryItem updatedItem) {
        logger.debug("Editing inventory item with id: {}", itemId);
        try {
            InventoryItem existingItem = inventoryItemRepository.findById(itemId)
                    .orElseThrow(() -> {
                        logger.warn("No inventory item found with id: {}", itemId);
                        return new IllegalArgumentException("Item does not exist");
                    });



            updatedItem.setInventoryItemId(itemId);
            updatedItem.setItemCode(existingItem.getItemCode());
            InventoryItem newItem = inventoryItemRepository.save(updatedItem);
            logger.info("Inventory item with id: {} successfully updated", itemId);
            return newItem;
        } catch (Exception e) {
            logger.error("Error while editing inventory item with id: {}: {}", itemId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<InventoryItem> searchInventoryItems(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return inventoryItemRepository.searchActiveInventoryItems(query, pageable);
    }

    @Override
    public String generateUniqueCode() {
        int year = Year.now().getValue();
        Integer latestSeq = itemCodeRepository.findMaxSequenceForYear(year);
        int newSeq = (latestSeq != null ? latestSeq + 1 : 1);

        String code = String.format("PEC%d%04d", year, newSeq);  // e.g. PEC20250001

        // Save to database
        ItemCode newEntry = new ItemCode();
        newEntry.setYear(year);
        newEntry.setSequenceNumber(newSeq);
        newEntry.setCode(code);
        itemCodeRepository.save(newEntry);

        return code;
    }
}
