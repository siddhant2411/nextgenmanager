package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.AddInventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.GroupedInventoryItem;
import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryPresentDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
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
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


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
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.BOOKED);
                booked.add(inst);
            }

            logger.info("Booked {} NOS instance(s) for item ID: {}", booked.size(), dbItem.getInventoryItemId());

        } else {
            logger.debug("Item ID: {} uses bulk UOM. Attempting to book required quantity: {}", dbItem.getInventoryItemId(), qty);
            InventoryInstance inst = inventoryInstanceRepository.findLatestInventoryInstance(dbItem.getInventoryItemId(), qty);

            if (inst != null) {
                inst.setBookedDate(now);
                inst.setQuantity(qty);
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.BOOKED);
                booked.add(inst);
                logger.info("Booked bulk instance ID: {} with quantity: {}", inst.getId(), qty);
            } else {
                logger.warn("No suitable bulk instance found to book quantity: {} for item ID: {}", qty, dbItem.getInventoryItemId());
            }
        }
        updateItemAvailability(item.getInventoryItemId());
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
    public List<InventoryInstance> consumeInventoryInstance(List<InventoryInstance> instances) {
        Date now = new Date();
        List<InventoryInstance> inventoryInstanceList = new ArrayList<>();
        for (InventoryInstance instance : instances) {
            instance.setConsumeDate(now);
            instance.setConsumed(true); // optional
            instance.setInventoryInstanceStatus(InventoryInstanceStatus.CONSUMED);
            inventoryInstanceList.add(inventoryInstanceRepository.save(instance));
        }

        return inventoryInstanceList;
    }

    @Override
    public void revertInventoryInstances(List<InventoryInstance> instances) {
        for (InventoryInstance instance : instances) {
            if (instance.isConsumed()) {
                throw new IllegalStateException("Cannot revert already consumed instance: " + instance.getId());
            }
            instance.setConsumed(false); // or reset to initial state
            instance.setBookedDate(null);
            instance.setConsumeDate(null);
            instance.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
            inventoryInstanceRepository.save(instance);
        }
    }

    @Override
    @Transactional
    public List<InventoryInstance> requestInstance(InventoryItem item, double qty, InventoryRequestSource source, Long sourceId) {
        logger.info("Requesting {} unit(s) for InventoryItem ID: {}", qty, item.getInventoryItemId());

        InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
        if (dbItem == null) {
            logger.error("Inventory item with ID {} not found", item.getInventoryItemId());
            throw new ResourceNotFoundException("Inventory item not found");
        }

        List<InventoryInstance> resultInstances = new ArrayList<>();
        Date now = new Date();

        // 1. Fetch available stock
        List<InventoryInstance> availableInstances = inventoryInstanceRepository.inventoryInstanceByStatus(InventoryInstanceStatus.CONSUMED,qty);

        if (isUnitByNos(dbItem)) {
            int requestedUnits = (int) qty;
            int fulfilled = 0;

            // 2. Book from available
            for (InventoryInstance inst : availableInstances) {
                if (fulfilled >= requestedUnits) break;

                inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                inst.setBookedDate(now);
                inst.setApprovalStatus(InventoryApprovalStatus.PENDING);
                inst.setRequestSource(source);
                inst.setLinkedSourceId(sourceId);
                resultInstances.add(inst);
                fulfilled++;
            }

            // 3. Request remaining
            int remaining = requestedUnits - fulfilled;
            for (int i = 0; i < remaining; i++) {
                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(dbItem);
                inst.setEntryDate(now);
                inst.setRequestedDate(now);
                inst.setQuantity(1);
                inst.setRequestSource(source);
                inst.setLinkedSourceId(sourceId);
                inst.setApprovalStatus(InventoryApprovalStatus.PENDING);
                inst.setProcurementDecision(ProcurementDecision.UNDECIDED);
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                resultInstances.add(inst);
            }

        } else {
            double availableQty = availableInstances.stream()
                    .mapToDouble(InventoryInstance::getQuantity)
                    .sum();

            if (availableQty >= qty) {
                // Fulfill entirely from existing
                double qtyToBook = qty;
                for (InventoryInstance inst : availableInstances) {
                    if (qtyToBook <= 0) break;

                    double instQty = inst.getQuantity();
                    double usedQty = Math.min(instQty, qtyToBook);
                    inst.setQuantity(usedQty); // Use full or partial
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.BOOKED);
                    inst.setBookedDate(now);
                    inst.setApprovalStatus(InventoryApprovalStatus.PENDING);
                    inst.setRequestSource(source);
                    inst.setLinkedSourceId(sourceId);
                    resultInstances.add(inst);

                    qtyToBook -= usedQty;
                }

            } else {
                // Partial fulfill + create request
                double qtyToBook = availableQty;
                double qtyToRequest = qty - availableQty;

                for (InventoryInstance inst : availableInstances) {
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.BOOKED);
                    inst.setBookedDate(now);
                    inst.setApprovalStatus(InventoryApprovalStatus.APPROVED);
                    inst.setRequestSource(source);
                    inst.setLinkedSourceId(sourceId);
                    resultInstances.add(inst);
                }

                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(dbItem);
                inst.setEntryDate(now);
                inst.setRequestedDate(now);
                inst.setQuantity(qtyToRequest);
                inst.setRequestSource(source);
                inst.setLinkedSourceId(sourceId);
                inst.setApprovalStatus(InventoryApprovalStatus.PENDING);
                inst.setProcurementDecision(ProcurementDecision.UNDECIDED);
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                resultInstances.add(inst);
            }
        }

        List<InventoryInstance> saved = inventoryInstanceRepository.saveAll(resultInstances);
        updateItemAvailability(dbItem.getInventoryItemId());
        logger.info("Request processed: {} instance(s) affected for InventoryItem ID: {}", saved.size(), dbItem.getInventoryItemId());

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
                    double totalCost = (double) record[7];

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
                            totalCost
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
    public Page<GroupedInventoryItem> getGroupedInventoryInstances(int page, int size, String sortBy, String sortDir,
                                                                   String queryItemCode, String queryItemName, String queryHsnCode,
                                                                   Double totalQuantityCondition, String filterType, UOM queryUOM,
                                                                   ItemType itemType,
                                                                   InventoryApprovalStatus approvalStatusFilter,
                                                                   ProcurementDecision procurementDecisionFilter)
    {
        try {
            logger.info("Fetching grouped inventory items");

            Integer uom = (queryUOM != null) ? queryUOM.ordinal() : null;
            Integer itemTypeValue = (itemType != null) ? itemType.ordinal() : null;

            String approvalStatusStr = (approvalStatusFilter != null) ? approvalStatusFilter.name() : null;
            String procurementDecisionStr = (procurementDecisionFilter != null) ? procurementDecisionFilter.name() : null;

            List<InventoryInstance> allInstances = inventoryInstanceRepository.getAllActiveInstancesFiltered(
                    queryItemCode, queryItemName, queryHsnCode, uom, itemTypeValue, approvalStatusStr, procurementDecisionStr);


            // Group by inventory item
            Map<Integer, List<InventoryInstance>> groupedByItem = allInstances.stream()
                    .filter(inst -> inst.getInventoryItem() != null)
                    .collect(Collectors.groupingBy(inst -> inst.getInventoryItem().getInventoryItemId()));

            // Apply quantity filter
            List<GroupedInventoryItem> groupedList = groupedByItem.values().stream()
                    .filter(instances -> {
                        double sum = instances.stream().mapToDouble(InventoryInstance::getQuantity).sum();
                        if (filterType == null || totalQuantityCondition == null) return true;
                        return switch (filterType) {
                            case "=" -> sum == totalQuantityCondition;
                            case ">" -> sum > totalQuantityCondition;
                            case "<" -> sum < totalQuantityCondition;
                            default -> true;
                        };
                    })
                    .map(instances -> new GroupedInventoryItem(instances.get(0).getInventoryItem(), instances))
                    .toList();

            // Manual pagination
            Pageable pageable = PageRequest.of(page, size);
            int start = Math.min((int) pageable.getOffset(), groupedList.size());
            int end = Math.min(start + pageable.getPageSize(), groupedList.size());

            List<GroupedInventoryItem> paged = groupedList.subList(start, end);
            return new PageImpl<>(paged, pageable, groupedList.size());

        } catch (Exception e) {
            logger.error("Error fetching grouped inventory instances", e);
            throw new RuntimeException("Failed to fetch grouped inventory instances", e);
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

    @Override
    @Transactional
    public List<InventoryInstance> markRequestedInventoryAsArrived(List<Long> instanceIds) {
        logger.info("Marking requested inventory instances as arrived: {}", instanceIds);

        List<InventoryInstance> updatedInstances = new ArrayList<>();
        Date now = new Date();

        for (Long id : instanceIds) {
            InventoryInstance instance = inventoryInstanceRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory Instance not found with ID: " + id));

            if (!InventoryInstanceStatus.REQUESTED.equals(instance.getInventoryInstanceStatus())) {
                logger.warn("Skipping instance ID {} as it is not in REQUESTED state.", id);
                continue;
            }

            instance.setEntryDate(now); // optional if not already set
            instance.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
            instance.setRequestedDate(null); // clear the requested date if needed

            updatedInstances.add(instance);
        }

        List<InventoryInstance> saved = inventoryInstanceRepository.saveAll(updatedInstances);
        logger.info("Successfully marked {} inventory instance(s) as arrived.", saved.size());

        // Update availability count for related items
        Set<Integer> affectedItemIds = saved.stream()
                .map(inst -> inst.getInventoryItem().getInventoryItemId())
                .collect(Collectors.toSet());
        affectedItemIds.forEach(this::updateItemAvailability);

        return saved;
    }

    public Map<String, Object> getInventorySummary(){
        Map<String, Object> summary = new HashMap<>();

        summary.put("totalItems", inventoryItemRepository.countByDeletedDateIsNull());

        summary.put("available", inventoryInstanceRepository
                .findAll().stream()
                .filter(i -> i.getInventoryInstanceStatus() == InventoryInstanceStatus.AVAILABLE)
                .mapToDouble(InventoryInstance::getQuantity).sum());

        summary.put("requested", inventoryInstanceRepository.countByInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED));
        summary.put("booked", inventoryInstanceRepository.countByInventoryInstanceStatus(InventoryInstanceStatus.BOOKED));
        summary.put("consumed", inventoryInstanceRepository.countByInventoryInstanceStatus(InventoryInstanceStatus.CONSUMED));
        summary.put("totalInventoryValue", inventoryInstanceRepository.countSumOfInventoryValue(InventoryInstanceStatus.CONSUMED));
        return summary;
    }



    @Transactional
    @Override
    public List<InventoryInstance> approveInventoryRequest(List<Long> instanceIds, InventoryRequestSource requestType, Long linkedOrderId) {
        List<InventoryInstance> updated = new ArrayList<>();
        Date now = new Date();

        for (Long id : instanceIds) {
            InventoryInstance instance = inventoryInstanceRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory Instance not found with ID: " + id));

            if (instance.getApprovalStatus() != InventoryApprovalStatus.PENDING) continue;

            instance.setApprovalStatus(InventoryApprovalStatus.APPROVED);
            instance.setRequestSource(requestType);

            // Optionally auto-book if decision is WORK_ORDER
            if (requestType == InventoryRequestSource.WORK_ORDER) {
                instance.setBookedDate(now);
                instance.setInventoryInstanceStatus(InventoryInstanceStatus.BOOKED);
            }

            updated.add(instance);
        }

        return inventoryInstanceRepository.saveAll(updated);
    }

    @Override
    @Transactional
    public List<InventoryInstance> requestInstanceByItemId(int itemId, double qty, InventoryRequestSource source, Long sourceId) {
        InventoryItem item = inventoryItemRepository.findByActiveId(itemId);
        if (item == null) {
            throw new ResourceNotFoundException("Inventory item not found with ID: " + itemId);
        }
        return requestInstance(item, qty, source, sourceId);
    }


    @Override
    @Transactional
    public List<InventoryInstance> addInventory(AddInventoryRequest request) {
        logger.info("Received request to add inventory: {}", request);

        int inventoryItemId = request.getInventoryItemId();
        double addedQty = request.getQuantity();
        double costPerUnit = request.getCostPerUnit();
        long referenceId = request.getReferenceId();
        ProcurementDecision procurementDecision = request.getProcurementDecision();

        if (addedQty <= 0) {
            logger.warn("Attempted to add invalid quantity: {}", addedQty);
            throw new IllegalArgumentException("Added quantity must be greater than zero.");
        }

        InventoryItem dbItem = inventoryItemRepository.findByActiveId(inventoryItemId);
        if (dbItem == null) {
            logger.error("Inventory item not found with id: {}", inventoryItemId);
            throw new ResourceNotFoundException("Inventory item not found with ID: " + inventoryItemId);
        }

        logger.info("Inventory item found: {} - {}", dbItem.getInventoryItemId(), dbItem.getName());

        List<InventoryInstance> instances = new ArrayList<>();
        Date now = new Date();

        double finalCost = costPerUnit > 0 ? costPerUnit :
                dbItem.getStandardCost() != null ? dbItem.getStandardCost() : 0;

        double finalPrice = dbItem.getSellingPrice() != null ? dbItem.getSellingPrice() : 0;

        try {
            if (isUnitByNos(dbItem)) {
                int unitCount = (int) addedQty;
                logger.info("Adding {} NOS-based inventory instances", unitCount);

                for (int i = 0; i < unitCount; i++) {
                    InventoryInstance inst = new InventoryInstance();
                    inst.setInventoryItem(dbItem);
                    inst.setEntryDate(now);
                    inst.setQuantity(1);
                    inst.setCostPerUnit(finalCost);
                    inst.setSellPricePerUnit(finalPrice);
                    inst.setLinkedSourceId(referenceId);
                    inst.setProcurementDecision(procurementDecision);
                    instances.add(inst);

                }
            } else {
                logger.info("Adding single quantity-based inventory instance with quantity {}", addedQty);
                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(dbItem);
                inst.setEntryDate(now);
                inst.setQuantity(addedQty);
                inst.setCostPerUnit(finalCost);
                inst.setSellPricePerUnit(finalPrice);
                inst.setLinkedSourceId(referenceId);
                inst.setProcurementDecision(procurementDecision);
                instances.add(inst);
            }

            List<InventoryInstance> saved = inventoryInstanceRepository.saveAll(instances);
            logger.info("Successfully saved {} inventory instances for item ID {}", saved.size(), inventoryItemId);

            updateItemAvailability(dbItem.getInventoryItemId());
            logger.info("Updated item availability for item ID {}", inventoryItemId);

            return saved;

        } catch (Exception ex) {
            logger.error("Error occurred while adding inventory instances", ex);
            throw new RuntimeException("Error occurred while adding inventory: " + ex.getMessage(), ex);
        }
    }


}

