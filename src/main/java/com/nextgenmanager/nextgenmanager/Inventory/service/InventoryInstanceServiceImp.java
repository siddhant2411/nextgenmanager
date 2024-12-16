package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class InventoryInstanceServiceImp implements InventoryInstanceService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryInstanceServiceImp.class);

    @Autowired
    private InventoryInstanceRepository inventoryInstanceRepository;
    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Override
    @Transactional
    public void createInventoryInstances(InventoryInstance inventoryInstance, double qty) {
        try {
            logger.info("Starting inventory instance creation for item ID: {}",
                    inventoryInstance.getInventoryItem().getInventoryItemId());

            InventoryItem inventoryItem = inventoryItemRepository.findByActiveId(
                    inventoryInstance.getInventoryItem().getInventoryItemId()
            );

            if (inventoryItem == null) {
                logger.error("Inventory item not found or inactive for ID: {}",
                        inventoryInstance.getInventoryItem().getInventoryItemId());
                throw new IllegalArgumentException("Invalid inventory item ID");
            }

            inventoryInstance.setInventoryItem(inventoryItem);

            if (inventoryInstance.getEntryDate() == null) {
                inventoryInstance.setEntryDate(new Date());
            }

            if (inventoryItem.getUom() == UOM.NOS) {
                List<InventoryInstance> instances = new ArrayList<>();
                for (int i = 0; i < (int) qty; i++) {
                    InventoryInstance newInstance = new InventoryInstance();
                    newInstance.setInventoryItem(inventoryItem);
                    newInstance.setEntryDate(inventoryInstance.getEntryDate());
                    newInstance.setQuantity(1);
                    instances.add(newInstance);
                }
                inventoryInstanceRepository.saveAll(instances);
                logger.info("Created {} inventory instances for item ID: {}",
                        instances.size(), inventoryItem.getInventoryItemId());
            } else {
                inventoryInstance.setQuantity(qty);
                inventoryInstanceRepository.save(inventoryInstance);
                logger.info("Created single inventory instance for item ID: {} with quantity: {}",
                        inventoryItem.getInventoryItemId(), qty);
            }
        } catch (Exception e) {
            logger.error("Error creating inventory instances: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create inventory instances", e);
        }
    }

    @Override
    public List<InventoryInstance> consumeInventoryInstance(InventoryItem inventoryItem, double consumedQty) {
        try {
            logger.info("Starting consumption of {} units for item ID: {}", consumedQty,
                    inventoryItem.getInventoryItemId());

            if (inventoryItem.getInventoryItemId()<0) {
                logger.error("Inventory item is null");
                throw new IllegalArgumentException("Inventory item cannot be null");
            }

            InventoryItem savedInventoryItem = inventoryItemRepository.findByActiveId(inventoryItem.getInventoryItemId());
            if (savedInventoryItem == null) {
                logger.error("Inventory item not found or inactive for ID: {}", inventoryItem.getInventoryItemId());
                throw new IllegalArgumentException("Invalid inventory item ID");
            }

            if (savedInventoryItem.getUom() == UOM.NOS) {
                int currentCount = inventoryInstanceRepository.countAvailableInInventory(savedInventoryItem.getInventoryItemId());
                logger.info("Current count of inventory for item ID {}: {}", savedInventoryItem.getInventoryItemId(), currentCount);

                if (currentCount >= consumedQty) {
                    List<InventoryInstance> itemsToConsume = inventoryInstanceRepository.getItemsToConsume(
                            savedInventoryItem.getInventoryItemId(), (int) consumedQty);
                    for (InventoryInstance item : itemsToConsume) {
                        item.setConsumeDate(new Date());
                        item.setQuantity(0);
                        item.setConsumed(true);
                    }
                    List<InventoryInstance> consumedItems = inventoryInstanceRepository.saveAll(itemsToConsume);
                    logger.info("Consumed {} inventory instances for item ID: {}", consumedItems.size(),
                            savedInventoryItem.getInventoryItemId());

                    List<InventoryInstance> itemsWithConsumedCount = new ArrayList<>();
                    for (InventoryInstance item : consumedItems) {
                        item.setQuantity(1);
                        itemsWithConsumedCount.add(item); // Add to the new list after modification
                    }
                    return itemsWithConsumedCount;
                } else {
                    logger.warn("Not enough inventory to consume. Required: {}, Available: {}", consumedQty, currentCount);
                    throw new IllegalStateException("Insufficient inventory for consumption");
                }
            } else {
                InventoryInstance itemsToConsume = inventoryInstanceRepository.findLatestInventoryInstance(
                        savedInventoryItem.getInventoryItemId(), consumedQty);
                if (itemsToConsume != null) {
                    itemsToConsume.setConsumeDate(new Date());
                    itemsToConsume.setQuantity(itemsToConsume.getQuantity() - consumedQty);
                    if(itemsToConsume.getQuantity()<=0){
                        itemsToConsume.setConsumed(true);
                    }
                    InventoryInstance consumedItem = inventoryInstanceRepository.save(itemsToConsume);
                    logger.info("Consumed inventory for item ID: {}. Remaining quantity: {}",
                            savedInventoryItem.getInventoryItemId(), itemsToConsume.getQuantity());

//                    changing for returning consumed item details
                    consumedItem.setQuantity(consumedQty);
                    return List.of(consumedItem);
                } else {
                    logger.warn("No suitable inventory instance found for consumption");
                    throw new IllegalStateException("Insufficient inventory for consumption");
                }
            }
        } catch (Exception e) {
            logger.error("Error consuming inventory instances: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to consume inventory instances", e);
        }
    }

    @Override
    public void updateInventoryInstance(InventoryInstance inventoryInstance) {
        // Implementation to be added with logging
        logger.warn("updateInventoryInstance method not implemented");
    }

    @Override
    public void deleteInventoryInstance(double id) {
        // Implementation to be added with logging
        logger.warn("deleteInventoryInstance method not implemented");
    }

    @Override
    public List<InventoryInstance> getAllInventoryInstances(int page, int size, String sortBy, String sortDir, String query) {
        // Placeholder implementation
        logger.warn("getAllInventoryInstances method not implemented");
        return List.of();
    }

    @Override
    public Page<InventoryInstance> getPresentInventoryInstances(int page, int size, String sortBy, String sortDir, String queryItemCode,
                                                                String queryItemName, String queryHsnCode, Double totalQuantityCondition,
                                                                String filterType, UOM queryUOM, ItemType itemType) {
        try {
            logger.info("Fetching paginated present inventory instances. Page: {}, Size: {}, SortBy: {}, SortDir: {}",
                    page, size, sortBy, sortDir);

            // Create a pageable object
            Pageable pageable = PageRequest.of(page, size,
                    sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

            Integer uom,itemTypeValue;
            if(queryUOM!=null){
                uom =queryUOM.ordinal();
            }else {
                uom=null;
            }
            if(itemType!=null){
                itemTypeValue = itemType.ordinal();
            }
            else {
                itemTypeValue = null;
            }


            // Fetch paginated data from repository
            Page<Object[]> inventoryListWithCount = inventoryInstanceRepository.getItemsWithTotalQuantity(pageable,queryItemCode,queryItemName,queryHsnCode,totalQuantityCondition,filterType,uom,itemTypeValue);

            // Process and map the results
            Page<InventoryInstance> inventoryInstances = inventoryListWithCount.map(record -> {
                int inventoryItemRef = (int) record[0];
                Double totalQuantity = (Double) record[1];

                InventoryItem inventoryItem = inventoryItemRepository.findById(inventoryItemRef)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid inventory item reference: " + inventoryItemRef));

                InventoryInstance instance = new InventoryInstance();
                instance.setInventoryItem(inventoryItem);
                instance.setQuantity(totalQuantity);
                instance.setUniqueId(null);
                return instance;
            });

            logger.info("Successfully fetched {} inventory items.", inventoryInstances.getTotalElements());
            return inventoryInstances;
        } catch (Exception e) {
            logger.error("Error fetching present inventory instances: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch present inventory instances. Please try again later.", e);
        }
    }

    @Override
    public List<InventoryInstance> getInventoryInstanceByItemId(int inventoryItemId,int page, int size, String sortBy, String sortDir, String query) {
        // Placeholder implementation
        logger.warn("getInventoryInstance method not implemented");
        return null;
    }
}
