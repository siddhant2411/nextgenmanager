package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryPresentDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.service.BomServiceException;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import io.swagger.models.auth.In;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;


@Service
public class InventoryInstanceServiceImp implements InventoryInstanceService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryInstanceServiceImp.class);

    @Autowired
    private InventoryInstanceRepository inventoryInstanceRepository;
    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @PersistenceContext
    private EntityManager entityManager;

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
                    newInstance.setCostPerUnit(inventoryInstance.getCostPerUnit());
                    newInstance.setSellPricePerUnit(inventoryInstance.getSellPricePerUnit());
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
            updateInventoryItemCount(inventoryItem.getInventoryItemId());
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
                    updateInventoryItemCount(inventoryItem.getInventoryItemId());
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
    public List<InventoryInstance> bookInventoryInstance(InventoryItem inventoryItem, double bookedQty) {
        try {
            logger.info("Starting booking of {} units for item ID: {}", bookedQty,
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


                List<InventoryInstance> bookedItems = new ArrayList<>();
                Pageable limit = PageRequest.of(0, (int) bookedQty);
                List<InventoryInstance> itemsToBook = inventoryInstanceRepository.getItemsToBook(savedInventoryItem.getInventoryItemId(), limit);

                for (InventoryInstance item : itemsToBook) {
//
                    item.setBookedDate(new Date());
                    bookedItems.add(inventoryInstanceRepository.save(item));
                }


                logger.info("Booked {} inventory instances for item ID: {}", bookedItems.size(),
                        savedInventoryItem.getInventoryItemId());

                List<InventoryInstance> itemsWithBookedCount = new ArrayList<>();
                for (InventoryInstance item : bookedItems) {
                    itemsWithBookedCount.add(item); // Add to the new list after modification
                }
                updateInventoryItemCount(inventoryItem.getInventoryItemId());
                return inventoryInstanceRepository.saveAll(itemsWithBookedCount);

            } else {
                InventoryInstance itemsToBook = inventoryInstanceRepository.findLatestInventoryInstance(
                        savedInventoryItem.getInventoryItemId(), bookedQty);
                if (itemsToBook != null) {
                    itemsToBook.setBookedDate(new Date());
                    InventoryInstance bookedItem = inventoryInstanceRepository.save(itemsToBook);
                    logger.info("Booked inventory for item ID: {}. Remaining quantity: {}",
                            savedInventoryItem.getInventoryItemId(), itemsToBook.getQuantity());

//                    changing for returning consumed item details
                    bookedItem.setQuantity(bookedQty);
                    updateInventoryItemCount(inventoryItem.getInventoryItemId());
                    return List.of(bookedItem);
                } else {
                    return new ArrayList<>();
                }
            }
        } catch (Exception e) {
            logger.error("Error consuming inventory instances: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to consume inventory instances", e);
        }
    }

    @Override
    @Transactional
    public List<InventoryInstance> requestInstance(InventoryItem inventoryItem, double requestedQty) {
        try {
            if (inventoryItem == null || inventoryItem.getInventoryItemId() < 0) {
                logger.error("Invalid inventory item provided");
                throw new IllegalArgumentException("Inventory item cannot be null or invalid");
            }

            logger.info("Starting request of {} units for item ID: {}", requestedQty, inventoryItem.getInventoryItemId());

            Date now = new Date();

            if (inventoryItem.getUom() == UOM.NOS) {
                List<InventoryInstance> instances = new ArrayList<>();
                for (int i = 0; i < (int) requestedQty; i++) {
                    InventoryInstance newInstance = new InventoryInstance();
                    newInstance.setInventoryItem(inventoryItem);
                    newInstance.setEntryDate(now);
                    newInstance.setRequestedDate(now);
                    newInstance.setBookedDate(now);
                    newInstance.setQuantity(1);
                    instances.add(newInstance);
                }
                List<InventoryInstance> savedInstances = inventoryInstanceRepository.saveAll(instances);
                logger.info("Requested {} inventory instances for item ID: {}", savedInstances.size(), inventoryItem.getInventoryItemId());
                updateInventoryItemCount(inventoryItem.getInventoryItemId());
                return savedInstances;
            } else {
                InventoryInstance newInstance = new InventoryInstance();
                newInstance.setInventoryItem(inventoryItem);
                newInstance.setEntryDate(now);
                newInstance.setRequestedDate(now);
                newInstance.setBookedDate(now);
                newInstance.setQuantity(requestedQty);
                InventoryInstance savedInventoryInstance = inventoryInstanceRepository.save(newInstance);
                logger.info("Requested single inventory instance for item ID: {} with quantity: {}", inventoryItem.getInventoryItemId(), savedInventoryInstance.getQuantity());
                updateInventoryItemCount(inventoryItem.getInventoryItemId());
                return List.of(savedInventoryInstance);
            }
        } catch (Exception e) {
            logger.error("Error requesting inventory instances: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to request inventory instances", e);
        }
    }



    @Override
    @Transactional
    public InventoryInstance updateInventoryInstance(InventoryInstance updatedInventoryInstance) {
        try {
            logger.info("Starting inventory instance update for item ID: {}",
                    updatedInventoryInstance.getInventoryItem().getInventoryItemId());

            InventoryItem inventoryItem = inventoryItemRepository.findByActiveId(
                    updatedInventoryInstance.getInventoryItem().getInventoryItemId()
            );

            if (inventoryItem == null) {
                logger.error("Inventory item not found or inactive for ID: {}",
                        updatedInventoryInstance.getInventoryItem().getInventoryItemId());
                throw new IllegalArgumentException("Invalid inventory item ID");
            }

            // Fetch existing inventory instances for the given item
            InventoryInstance existingInstance = getInventoryInstanceById(updatedInventoryInstance.getId());
            existingInstance.setConsumed(updatedInventoryInstance.isConsumed());
            existingInstance.setQuantity(updatedInventoryInstance.getQuantity());
            if (updatedInventoryInstance.getInventoryItem() != null) {
                existingInstance.setInventoryItem(inventoryItem);
            }
            if (updatedInventoryInstance.getUniqueId() != null) {
                existingInstance.setUniqueId(updatedInventoryInstance.getUniqueId());
            }
            if (updatedInventoryInstance.getConsumeDate() != null) {
                existingInstance.setConsumeDate(updatedInventoryInstance.getConsumeDate());
            }
            if (updatedInventoryInstance.getEntryDate() != null) {
                existingInstance.setEntryDate(updatedInventoryInstance.getEntryDate());
            }
            if (updatedInventoryInstance.getDeletedDate() != null) {
                existingInstance.setDeletedDate(updatedInventoryInstance.getDeletedDate());
            }
            if (updatedInventoryInstance.getCostPerUnit() != null) {
                existingInstance.setCostPerUnit(updatedInventoryInstance.getCostPerUnit());
            }
            if (updatedInventoryInstance.getSellPricePerUnit() != null) {
                existingInstance.setSellPricePerUnit(updatedInventoryInstance.getSellPricePerUnit());
            }
            inventoryInstanceRepository.save(existingInstance);

            logger.info("Updated inventory instance for item ID: {}",
                    inventoryItem.getInventoryItemId());
            updateInventoryItemCount(inventoryItem.getInventoryItemId());
            return existingInstance;
        } catch (Exception e) {
            logger.error("Error updating inventory instances: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update inventory instances", e);
        }
    }


    @Override
    public void deleteInventoryInstance(long id) {
        logger.info("Starting deletion of Inventory Instance with ID: {}", id);

        try {
            InventoryInstance inventoryInstance = getInventoryInstanceById(id); // This will throw if not found

            // Soft delete - update the deletedDate
            inventoryInstance.setDeletedDate(new Date());

            // Save the updated BOM
            inventoryInstanceRepository.save(inventoryInstance);
            logger.info("Successfully soft-deleted BOM with ID: {}", id);
            updateInventoryItemCount(inventoryInstance.getInventoryItem().getInventoryItemId());

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error while deleting Inventory Instance with ID {}: {}", id, e.getMessage());
            throw new BomServiceException("Failed to delete Inventory Instance", e);
        }
    }

    @Override
    public List<InventoryInstance> getAllInventoryInstances(int page, int size, String sortBy, String sortDir, String query) {
        // Placeholder implementation
        logger.warn("getAllInventoryInstances method not implemented");
        return List.of();
    }

    @Override
    public Page<InventoryPresentDTO> getPresentInventoryInstances(int page, int size, String sortBy, String sortDir,
                                                                  String queryItemCode, String queryItemName, String queryHsnCode,
                                                                  Double totalQuantityCondition, String filterType, UOM queryUOM,
                                                                  ItemType itemType) {
        try {
            logger.info("Fetching paginated present inventory instances. Page: {}, Size: {}, SortBy: {}, SortDir: {}",
                    page, size, sortBy, sortDir);

            // Create a pageable object
            Pageable pageable = PageRequest.of(page, size,
                    sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

            // Convert UOM and ItemType to their ordinal values if not null
            Integer uom = (queryUOM != null) ? queryUOM.ordinal() : null;
            Integer itemTypeValue = (itemType != null) ? itemType.ordinal() : null;

            // Fetch raw data from repository
            Page<Object[]> inventoryListWithCount = inventoryInstanceRepository.getItemsForInventoryPage(
                    pageable, queryItemCode, queryItemName, queryHsnCode, totalQuantityCondition, filterType, uom, itemTypeValue);

            // Map raw data to InventoryPresentDTO
            Page<InventoryPresentDTO> inventoryPresentDTOPage = inventoryListWithCount.map(record -> {
                try {
                    int inventoryItemRef = (int)record[0];
                    double totalQuantity = (double) record[6];
                    double averageCost = (double)record[7];

                    // Fetch inventory item details
                    InventoryItem inventoryItem = inventoryItemRepository.findByActiveId(inventoryItemRef);
                    if(inventoryItem==null){
                        throw new RuntimeException("Invalid inventory item reference: " + inventoryItemRef);
                    }

                    // Map to DTO
                    return new InventoryPresentDTO(
                            inventoryItem.getInventoryItemId(),
                            inventoryItem.getItemCode(),
                            inventoryItem.getName(),
                            inventoryItem.getHsnCode(),
                            inventoryItem.getItemType(),
                            inventoryItem.getUom(),
                            totalQuantity,
                            averageCost
                    );
                } catch (Exception e) {
                    // Add logging here
                    throw new RuntimeException("Error mapping inventory data: " + e.getMessage(), e);
                }
            });

            logger.info("Successfully fetched {} inventory items.", inventoryPresentDTOPage.getTotalElements());
            return inventoryPresentDTOPage;
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

    @Override
    public InventoryInstance getInventoryInstanceById(long id){
        logger.info("Fetching Inventory Instance with ID: {}", id);


        return inventoryInstanceRepository.findById(id)
                .filter(bom -> bom.getDeletedDate() == null)
                .orElseThrow(() -> {
                    logger.error("Inventory Instance not found or deleted with ID: {}", id);
                    return new ResourceNotFoundException("Inventory Instance not found with ID: " + id);
                });

    }


    private void updateInventoryItemCount(int id) {
        InventoryItem inventoryItem = inventoryItemRepository.findByActiveId(id);

        if (inventoryItem == null) {
            throw new EntityNotFoundException("InventoryItem not found with id: " + id);
        }

        double currentCount = 0;

        if (inventoryItem.getUom() == UOM.NOS) {
            currentCount = inventoryInstanceRepository.countAvailableInInventory(id);
        } else {
            currentCount = inventoryInstanceRepository.getTotalQuantityForNonNOSItem(id);
        }

        inventoryItem.setAvailableQuantity(currentCount);
        inventoryItemRepository.save(inventoryItem);
    }

}

