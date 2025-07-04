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

import java.time.LocalDate;
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

    private boolean isUnitByNos(InventoryItem item) {
        return item.getUom() == UOM.NOS;
    }

    @Override
    public void updateItemAvailability(int itemId) {
        InventoryItem item = inventoryItemRepository.findByActiveId(itemId);

        if (item == null) {
            throw new ResourceNotFoundException("InventoryItem not found with ID: " + itemId);
        }

        double availableQty;

        if (item.getUom() == UOM.NOS) {
            availableQty = inventoryInstanceRepository.countAvailableInInventory(itemId);
        } else {
            availableQty = inventoryInstanceRepository.getTotalQuantityForNonNOSItem(itemId);
        }

        item.setAvailableQuantity(availableQty);
        inventoryItemRepository.save(item);

        logger.info("Updated available quantity for item ID {}: {}", itemId, availableQty);
    }

    @Override
    @Transactional
    public List<InventoryInstance> createInstances(InventoryItem item, double qty, InventoryInstance template) {
        InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
        if (dbItem == null) throw new ResourceNotFoundException("Inventory item not found");

        List<InventoryInstance> instances = new ArrayList<>();
        Date now = new Date();

        if (isUnitByNos(dbItem)) {
            for (int i = 0; i < (int) qty; i++) {
                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(dbItem);
                inst.setEntryDate(now);
                inst.setQuantity(1);
                inst.setCostPerUnit(
                        template.getCostPerUnit() != null ? template.getCostPerUnit() : item.getStandardCost()
                );
                inst.setSellPricePerUnit(
                        template.getSellPricePerUnit()  != null ?
                                template.getSellPricePerUnit() : item.getSellingPrice());
                instances.add(inst);
            }
        } else {
            InventoryInstance inst = new InventoryInstance();
            inst.setInventoryItem(dbItem);
            inst.setEntryDate(now);
            inst.setQuantity(qty);
            inst.setCostPerUnit(
                    template.getCostPerUnit() != null ? template.getCostPerUnit() : item.getStandardCost()
            );
            inst.setSellPricePerUnit(
                    template.getSellPricePerUnit()  != null ?
                            template.getSellPricePerUnit() : item.getSellingPrice());
            instances.add(inst);
        }
        List<InventoryInstance> saved = inventoryInstanceRepository.saveAll(instances);
        updateItemAvailability(dbItem.getInventoryItemId());
        return saved;
    }

    @Override
    @Transactional
    public List<InventoryInstance> bookInventoryInstance(InventoryItem item, double qty) {
        logger.info("Attempting to book {} unit(s) for inventory item ID: {}", qty, item.getInventoryItemId());

        InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
        if (dbItem == null) {
            logger.error("Inventory item not found for ID: {}", item.getInventoryItemId());
            throw new ResourceNotFoundException("Inventory item not found");
        }

        List<InventoryInstance> booked = new ArrayList<>();
        Date now = new Date();

        if (isUnitByNos(dbItem)) {
            logger.debug("Item ID: {} uses UOM.NOS. Fetching available unbooked instances...", dbItem.getInventoryItemId());
            List<InventoryInstance> available = inventoryInstanceRepository.getItemsToBook(dbItem.getInventoryItemId(), Pageable.ofSize((int) qty));

            if (available.size() < qty) {
                logger.warn("Only {} available instances found for booking (requested: {}).", available.size(), qty);
            }

            for (InventoryInstance inst : available) {
                inst.setBookedDate(now);
                booked.add(inst);
            }

            logger.info("Booked {} NOS instance(s) for item ID: {}", booked.size(), dbItem.getInventoryItemId());

        } else {
            logger.debug("Item ID: {} uses bulk UOM. Attempting to book required quantity: {}", dbItem.getInventoryItemId(), qty);
            InventoryInstance inst = inventoryInstanceRepository.findLatestInventoryInstance(dbItem.getInventoryItemId(), qty);

            if (inst != null) {
                inst.setBookedDate(now);
                inst.setQuantity(qty);
                booked.add(inst);
                logger.info("Booked bulk instance ID: {} with quantity: {}", inst.getId(), qty);
            } else {
                logger.warn("No suitable bulk instance found to book quantity: {} for item ID: {}", qty, dbItem.getInventoryItemId());
            }
        }

        return inventoryInstanceRepository.saveAll(booked);
    }

    @Override
    @Transactional
    public List<InventoryInstance> consumeInventoryInstance(InventoryItem item, double qty) {
        logger.info("Consuming {} unit(s) for inventory item ID: {}", qty, item.getInventoryItemId());

        InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
        if (dbItem == null) {
            logger.error("Inventory item not found with ID: {}", item.getInventoryItemId());
            throw new ResourceNotFoundException("Inventory item not found");
        }

        List<InventoryInstance> consumed = new ArrayList<>();
        Date now = new Date();

        if (isUnitByNos(dbItem)) {
            logger.debug("Item ID: {} is UOM.NOS. Fetching up to {} available instances.", dbItem.getInventoryItemId(), (int) qty);
            List<InventoryInstance> available = inventoryInstanceRepository.getItemsToConsume(dbItem.getInventoryItemId(), (int) qty);

            for (InventoryInstance inst : available) {
                inst.setConsumed(true);
                inst.setConsumeDate(now);
                inst.setQuantity(0);
                consumed.add(inst);
            }

            logger.info("Consumed {} NOS instance(s) for item ID: {}", consumed.size(), dbItem.getInventoryItemId());
        } else {
            logger.debug("Item ID: {} is bulk. Attempting to consume quantity: {}", dbItem.getInventoryItemId(), qty);
            InventoryInstance inst = inventoryInstanceRepository.findLatestInventoryInstance(dbItem.getInventoryItemId(), qty);

            if (inst != null) {
                inst.setConsumeDate(now);
                inst.setQuantity(inst.getQuantity() - qty);
                inst.setConsumed(inst.getQuantity() <= 0);
                inst.setQuantity(qty); // for tracking consumed qty
                consumed.add(inst);

                logger.info("Consumed bulk instance ID: {} with quantity: {} for item ID: {}", inst.getId(), qty, dbItem.getInventoryItemId());
            } else {
                logger.warn("No suitable bulk inventory instance found for item ID: {} and quantity: {}", dbItem.getInventoryItemId(), qty);
            }
        }

        inventoryInstanceRepository.saveAll(consumed);
        updateItemAvailability(dbItem.getInventoryItemId());
        return consumed;
    }


    @Override
    public void consumeInventoryInstance(List<InventoryInstance> instances) {
        Date now = new Date();
        for (InventoryInstance instance : instances) {
            instance.setConsumeDate(now);
            instance.setConsumed(true); // optional
            inventoryInstanceRepository.save(instance);
        }
    }

    @Override
    public void revertInventoryInstances(List<InventoryInstance> instances) {
        for (InventoryInstance instance : instances) {
            if (instance.isConsumed()) {
                throw new IllegalStateException("Cannot revert already consumed instance: " + instance.getId());
            }
            instance.setConsumed(false); // or reset to initial state
            instance.setBookedDate(null);
            instance.setConsumeDate(null);// optional
            inventoryInstanceRepository.save(instance);
        }
    }

    @Override
    @Transactional
    public List<InventoryInstance> requestInstance(InventoryItem item, double qty) {
        logger.info("Requesting {} unit(s) for InventoryItem ID: {}", qty, item.getInventoryItemId());

        InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
        if (dbItem == null) {
            logger.error("Inventory item with ID {} not found", item.getInventoryItemId());
            throw new ResourceNotFoundException("Inventory item not found");
        }

        List<InventoryInstance> requested = new ArrayList<>();
        Date now = new Date();

        if (isUnitByNos(dbItem)) {
            logger.debug("Inventory item ID {} is of unit type NOS. Creating {} instances.", dbItem.getInventoryItemId(), (int) qty);
            for (int i = 0; i < (int) qty; i++) {
                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(dbItem);
                inst.setEntryDate(now);
                inst.setRequestedDate(now);
                inst.setBookedDate(now);
                inst.setQuantity(1);
                requested.add(inst);
            }
        } else {
            logger.debug("Inventory item ID {} is not of unit type NOS. Creating single instance with quantity {}.", dbItem.getInventoryItemId(), qty);
            InventoryInstance inst = new InventoryInstance();
            inst.setInventoryItem(dbItem);
            inst.setEntryDate(now);
            inst.setRequestedDate(now);
            inst.setBookedDate(now);
            inst.setQuantity(qty);
            requested.add(inst);
        }

        List<InventoryInstance> saved = inventoryInstanceRepository.saveAll(requested);
        logger.info("Successfully requested {} instance(s) for InventoryItem ID: {}", saved.size(), dbItem.getInventoryItemId());

        updateItemAvailability(dbItem.getInventoryItemId());
        return saved;
    }




    @Override
    public InventoryInstance updateInventoryInstance(InventoryInstance updated) {
        if (updated == null || updated.getId() == null) {
            logger.warn("Update request received with null or missing ID: {}", updated);
            throw new IllegalArgumentException("Inventory instance or ID must not be null");
        }

        InventoryInstance existing = getInventoryInstanceById(updated.getId());
        boolean modified = false;

        if (updated.getQuantity() != existing.getQuantity()) {
            logger.debug("Updating quantity for instance ID {}: {} -> {}", existing.getId(), existing.getQuantity(), updated.getQuantity());
            existing.setQuantity(updated.getQuantity());
            modified = true;
        }
        if (updated.getCostPerUnit() != null && !updated.getCostPerUnit().equals(existing.getCostPerUnit())) {
            logger.debug("Updating costPerUnit for instance ID {}: {} -> {}", existing.getId(), existing.getCostPerUnit(), updated.getCostPerUnit());
            existing.setCostPerUnit(updated.getCostPerUnit());
            modified = true;
        }
        if (updated.getSellPricePerUnit() != null && !updated.getSellPricePerUnit().equals(existing.getSellPricePerUnit())) {
            logger.debug("Updating sellPricePerUnit for instance ID {}: {} -> {}", existing.getId(), existing.getSellPricePerUnit(), updated.getSellPricePerUnit());
            existing.setSellPricePerUnit(updated.getSellPricePerUnit());
            modified = true;
        }
        if (updated.getConsumeDate() != null && !updated.getConsumeDate().equals(existing.getConsumeDate())) {
            logger.debug("Updating consumeDate for instance ID {}: {} -> {}", existing.getId(), existing.getConsumeDate(), updated.getConsumeDate());
            existing.setConsumeDate(updated.getConsumeDate());
            modified = true;
        }
        if (updated.isConsumed() != existing.isConsumed()) {
            logger.debug("Updating consumed flag for instance ID {}: {} -> {}", existing.getId(), existing.isConsumed(), updated.isConsumed());
            existing.setConsumed(updated.isConsumed());
            modified = true;
        }
        if (updated.getDeletedDate() != null && existing.getDeletedDate() == null) {
            logger.debug("Marking instance ID {} as deleted with date: {}", existing.getId(), updated.getDeletedDate());
            existing.setDeletedDate(updated.getDeletedDate());
            modified = true;
        }

        if (modified) {
            logger.info("Saving updates to inventory instance ID: {}", existing.getId());
            return inventoryInstanceRepository.save(existing);
        } else {
            logger.info("No changes detected for inventory instance ID: {}. Skipping save.", existing.getId());
            return existing;
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
        logger.info("Fetching all inventory instances. Page: {}, Size: {}, SortBy: {}, SortDir: {}, Query: {}", page, size, sortBy, sortDir, query);
        Pageable pageable = PageRequest.of(page, size, sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());
        Page<InventoryInstance> pageResult = inventoryInstanceRepository.findAll(pageable);
        return pageResult.getContent();
    }
    @Override
    public Page<InventoryPresentDTO> getPresentInventoryInstances(int page, int size, String sortBy, String sortDir,
                                                                  String queryItemCode, String queryItemName, String queryHsnCode,
                                                                  Double totalQuantityCondition, String filterType, UOM queryUOM,
                                                                  ItemType itemType) {
        try {
            logger.info("Fetching paginated present inventory instances. Page: {}, Size: {}, SortBy: {}, SortDir: {}", page, size, sortBy, sortDir);

            Pageable pageable = PageRequest.of(page, size,
                    sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

            Integer uom = (queryUOM != null) ? queryUOM.ordinal() : null;
            Integer itemTypeValue = (itemType != null) ? itemType.ordinal() : null;

            Page<Object[]> inventoryListWithCount = inventoryInstanceRepository.getItemsForInventoryPage(
                    pageable, queryItemCode, queryItemName, queryHsnCode, totalQuantityCondition, filterType, uom, itemTypeValue);

            Page<InventoryPresentDTO> inventoryPresentDTOPage = inventoryListWithCount.map(record -> {
                try {
                    int inventoryItemRef = (int) record[0];
                    double totalQuantity = (double) record[6];
                    double averageCost = (double) record[7];

                    InventoryItem inventoryItem = inventoryItemRepository.findByActiveId(inventoryItemRef);
                    if (inventoryItem == null) {
                        logger.error("Invalid inventory item reference: {}", inventoryItemRef);
                        throw new ResourceNotFoundException("Invalid inventory item reference: " + inventoryItemRef);
                    }

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
                    logger.error("Error mapping inventory data: {}", e.getMessage(), e);
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
    public InventoryInstance getInventoryInstanceById(long id) {
        logger.info("Fetching Inventory Instance with ID: {}", id);

        return inventoryInstanceRepository.findById(id)
                .filter(inst -> inst.getDeletedDate() == null)
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

