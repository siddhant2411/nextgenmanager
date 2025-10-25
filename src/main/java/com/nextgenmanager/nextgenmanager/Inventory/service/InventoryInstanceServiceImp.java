package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.*;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryBookingApprovalRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryProcurementOrderRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
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

    @Autowired
    private InventoryRequestRepository inventoryRequestRepository;

    @Autowired
    private InventoryBookingApprovalRepository inventoryBookingApprovalRepository;

    @Autowired
    private InventoryProcurementOrderRepository inventoryProcurementOrderRepository;
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
            availableQty = inventoryInstanceRepository.countAvailableInInventory(itemId,InventoryInstanceStatus.AVAILABLE.name());
        } else {
            availableQty = inventoryInstanceRepository.getTotalQuantityForNonNOSItem(itemId);
        }

        item.getProductInventorySettings().setAvailableQuantity(availableQty);
        inventoryItemRepository.save(item);

        logger.info("Updated available quantity for item ID {}: {}", itemId, availableQty);
    }

    @Override
    public void revertInventoryInstances(List<InventoryInstance> instances) {

        List<InventoryInstance> inventoryInstanceList = new ArrayList<>();
        for(InventoryInstance instance: instances){
            InventoryInstance inst  = inventoryInstanceRepository.getReferenceById(instance.getId());
            inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
            inst.setConsumed(false);
            inst.setBookedDate(null);
            inst.setConsumeDate(null);
            inventoryInstanceList.add(inst);
        }

        inventoryInstanceRepository.saveAll(inventoryInstanceList);
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
                        template.getCostPerUnit() != null ? template.getCostPerUnit() : item.getProductFinanceSettings().getStandardCost()
                );
                inst.setSellPricePerUnit(
                        template.getSellPricePerUnit()  != null ?
                                template.getSellPricePerUnit() : item.getProductFinanceSettings().getStandardCost());
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                instances.add(inst);

            }
        } else {
            InventoryInstance inst = new InventoryInstance();
            inst.setInventoryItem(dbItem);
            inst.setEntryDate(now);
            inst.setQuantity(qty);
            inst.setCostPerUnit(
                    template.getCostPerUnit() != null ? template.getCostPerUnit() : item.getProductFinanceSettings().getStandardCost()
            );
            inst.setSellPricePerUnit(
                    template.getSellPricePerUnit()  != null ?
                            template.getSellPricePerUnit() : item.getProductFinanceSettings().getStandardCost());
            inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
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
    public List<InventoryInstance> consumeInventoryInstance(InventoryItem item, double qty, Long requestId) {
        logger.info("Consuming {} unit(s) for inventory item ID: {}", qty, item.getInventoryItemId());

        InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
        if (dbItem == null) {
            logger.error("Inventory item not found with ID: {}", item.getInventoryItemId());
            throw new ResourceNotFoundException("Inventory item not found");
        }

        InventoryRequest request = inventoryRequestRepository.getReferenceById(requestId);

        if (request.getRequestedInstances() == null || request.getRequestedInstances().isEmpty()) {
            throw new IllegalArgumentException("Invalid inventory request or no instances provided.");
        }

        List<InventoryInstance> consumed = new ArrayList<>();
        Date now = new Date();

        if (isUnitByNos(dbItem)) {
            logger.debug("Item ID: {} is UOM.NOS. Fetching up to {} available instances.", dbItem.getInventoryItemId(), (int) qty);
            List<InventoryInstance> available = request.getRequestedInstances();
            int count =0;
            for (InventoryInstance inst : available) {
                if(count>=qty){
                    break;
                }
                if(inst.getInventoryItem().equals(dbItem)){
                    inst.setConsumed(true);
                    inst.setConsumeDate(now);
                    inst.setQuantity(0);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.CONSUMED);
                    consumed.add(inst);
                    count++;
                }

            }

            logger.info("Consumed {} NOS instance(s) for item ID: {}", consumed.size(), dbItem.getInventoryItemId());
        } else {
            logger.debug("Item ID: {} is bulk. Attempting to consume quantity: {}", dbItem.getInventoryItemId(), qty);
            List<InventoryInstance> instanceList = request.getRequestedInstances();
            for (InventoryInstance instance : instanceList) {
                if (!instance.getInventoryItem().equals(dbItem)) continue;
                if (qty <= 0) break;

                double availableQty = instance.getQuantity();
                double toConsume = Math.min(qty, availableQty);

                instance.setConsumeDate(now);
                instance.setQuantity(availableQty - toConsume);
                instance.setConsumed(instance.getQuantity() == 0);
                if (instance.getQuantity() == 0) {
                    instance.setInventoryInstanceStatus(InventoryInstanceStatus.CONSUMED);
                }
                consumed.add(instance);

                qty -= toConsume;


            }


        }

        inventoryInstanceRepository.saveAll(consumed);
        updateItemAvailability(dbItem.getInventoryItemId());
        return consumed;
    }



    @Override
    @Transactional
    public InventoryRequest requestInstance(
            InventoryItem item,
            double qty,
            InventoryRequestSource source,
            Long sourceId,
            String requestedBy,
            String requestRemarks) {

        logger.info("Requesting {} unit(s) for InventoryItem ID: {}", qty, item.getInventoryItemId());

        // ✅ Step 0: Validate and fetch item
        InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
        if (dbItem == null) {
            logger.error("Inventory item with ID {} not found", item.getInventoryItemId());
            throw new ResourceNotFoundException("Inventory item not found");
        }

        if (qty <= 0) {
            throw new IllegalArgumentException("Requested quantity must be greater than zero.");
        }

        Date now = new Date();
        List<InventoryInstance> resultInstances = new ArrayList<>();

        // ✅ Step 1: Create and save the request entity
        InventoryRequest request = new InventoryRequest();
        request.setRequestSource(source);
        request.setSourceId(sourceId);
        request.setRequestedDate(now);
        request.setRequestedBy(requestedBy);
        request.setRequestRemarks(requestRemarks);
        request.setApprovalStatus(InventoryApprovalStatus.PENDING);
        request.setReferenceNumber("REQ-" + UUID.randomUUID().toString().substring(0, 8));
        request.setInventoryItem(dbItem);
        request = inventoryRequestRepository.save(request);

        // ✅ Step 2: Fetch available instances
        List<InventoryInstance> availableInstances =
                inventoryInstanceRepository.inventoryInstanceByStatus(
                        dbItem.getInventoryItemId(),
                        InventoryInstanceStatus.AVAILABLE.name(),
                        qty);

        // ✅ Step 3: Process by unit type
        if (isUnitByNos(dbItem)) {
            int requestedUnits = (int) qty;
            int fulfilled = 0;

            // Fill from available
            for (InventoryInstance inst : availableInstances) {
                if (fulfilled >= requestedUnits) break;
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                inst.setBookedDate(now);
                inst.setInventoryRequest(request);
                resultInstances.add(inst);
                fulfilled++;
            }

            // Create pending instances if shortfall
            int remaining = requestedUnits - fulfilled;
            for (int i = 0; i < remaining; i++) {
                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(dbItem);
                inst.setEntryDate(now);
                inst.setQuantity(1);
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.PENDING);
                inst.setInventoryRequest(request);
                resultInstances.add(inst);
            }

        } else {
            double availableQty = availableInstances.stream()
                    .mapToDouble(InventoryInstance::getQuantity)
                    .sum();

            if (availableQty >= qty) {
                // enough available, just mark as requested
                double qtyToBook = qty;
                for (InventoryInstance inst : availableInstances) {
                    if (qtyToBook <= 0) break;
                    double instQty = inst.getQuantity();
                    double usedQty = Math.min(instQty, qtyToBook);

                    inst.setQuantity(usedQty);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                    inst.setBookedDate(now);
                    inst.setInventoryRequest(request);
                    resultInstances.add(inst);

                    qtyToBook -= usedQty;
                }
            } else {
                // not enough available, book what you can and create pending
                double qtyToRequest = qty - availableQty;

                for (InventoryInstance inst : availableInstances) {
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                    inst.setBookedDate(now);
                    inst.setInventoryRequest(request);
                    resultInstances.add(inst);
                }

                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(dbItem);
                inst.setEntryDate(now);
                inst.setQuantity(qtyToRequest);
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.PENDING);
                inst.setInventoryRequest(request);
                resultInstances.add(inst);
            }
        }

        // ✅ Step 4: Persist instances and update request
        List<InventoryInstance> savedInstances = inventoryInstanceRepository.saveAll(resultInstances);
        request.setRequestedInstances(savedInstances);
        InventoryRequest savedRequest = inventoryRequestRepository.save(request);

        // ✅ Step 5: Update availability
        updateItemAvailability(dbItem.getInventoryItemId());

        // ✅ Step 6: Create procurement order if there are pending instances
        List<InventoryInstance> pendingInstances = savedInstances.stream()
                .filter(inst -> inst.getInventoryInstanceStatus() == InventoryInstanceStatus.PENDING)
                .toList();

        if (!pendingInstances.isEmpty()) {
            InventoryProcurementOrder procurementOrder = new InventoryProcurementOrder();
            procurementOrder.setInventoryRequest(savedRequest);
            procurementOrder.setPendingInventoryList(pendingInstances);
            procurementOrder.setCreatedBy(requestedBy);
            procurementOrder.setInventoryItem(dbItem);
            procurementOrder.setInventoryProcurementStatus(InventoryProcurementStatus.CREATED);
            inventoryProcurementOrderRepository.save(procurementOrder);
            logger.info("Created procurement order for {} pending instance(s)", pendingInstances.size());
        }

        logger.info("Request processed: {} instance(s) affected for InventoryItem ID: {}",
                savedInstances.size(), dbItem.getInventoryItemId());

        updateItemAvailability(dbItem.getInventoryItemId());
        return savedRequest;
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
            updateItemAvailability(inventoryInstance.getInventoryItem().getInventoryItemId());

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
                    queryItemCode, queryItemName, queryHsnCode, uom, itemTypeValue);


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
            currentCount = inventoryInstanceRepository.countAvailableInInventory(id,InventoryInstanceStatus.AVAILABLE.name());
        } else {
            currentCount = inventoryInstanceRepository.getTotalQuantityForNonNOSItem(id);
        }

        inventoryItem.getProductInventorySettings().setAvailableQuantity(currentCount);
        inventoryItemRepository.save(inventoryItem);
    }

    public Map<String, Object> getInventorySummary(){
        Map<String, Object> summary = new HashMap<>();

        summary.put("totalItems", inventoryItemRepository.countByDeletedDateIsNull());

        summary.put("available", inventoryInstanceRepository
                .findAll().stream()
                .filter(i -> i.getInventoryInstanceStatus() == InventoryInstanceStatus.AVAILABLE)
                .mapToDouble(InventoryInstance::getQuantity).sum());

        summary.put("requested", inventoryInstanceRepository.countByInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED.name()));
        summary.put("booked", inventoryInstanceRepository.countByInventoryInstanceStatus(InventoryInstanceStatus.BOOKED.name()));
        summary.put("consumed", inventoryInstanceRepository.countByInventoryInstanceStatus(InventoryInstanceStatus.CONSUMED.name()));
        summary.put("totalInventoryValue", inventoryInstanceRepository.countSumOfInventoryValue());
        return summary;
    }



    @Transactional
    @Override
    public List<InventoryInstance> approveInventoryRequest(Long requestId, String approvedBy,String approveRemarks) {
        InventoryRequest request = inventoryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory Request not found with ID: " + requestId));

        List<InventoryInstance> instances = request.getRequestedInstances();

        // Check that all instances are still REQUESTED
        boolean pendingItem = instances.stream()
                .anyMatch(i -> i.getInventoryInstanceStatus() == InventoryInstanceStatus.PENDING);

        if (pendingItem) {
            throw new IllegalStateException("Cannot approve: Some instances are already processed or pending.");
        }

        InventoryRequestSource requestType = request.getRequestSource();
        Long linkedSourceId = request.getSourceId();
        Date now = new Date();


        for (InventoryInstance instance : instances) {


            instance.setInventoryInstanceStatus(InventoryInstanceStatus.BOOKED);
            instance.setBookedDate(now);

        }

        List<InventoryInstance> savedInstances = inventoryInstanceRepository.saveAll(instances);

        // Update request status
        request.setApprovalStatus(InventoryApprovalStatus.APPROVED);
        inventoryRequestRepository.save(request);

        // Create approval audit record
        InventoryBookingApproval approval = new InventoryBookingApproval();
        approval.setInventoryRequest(request);
        approval.setApprovalDate(now);
        approval.setApprovedBy(approvedBy); // Replace with logged-in username if available
        approval.setApprovalStatus(InventoryApprovalStatus.APPROVED);
        approval.setApprovalRemarks(approveRemarks);
        inventoryBookingApprovalRepository.save(approval);
        updateItemAvailability(request.getInventoryItem().getInventoryItemId());
        return savedInstances;
    }



    @Transactional
    @Override
    public List<InventoryInstance> rejectInventoryRequest(Long requestId, String rejectedBy, String rejectRemarks) {
        // Fetch the request
        InventoryRequest request = inventoryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory Request not found with ID: " + requestId));

        List<InventoryInstance> instances = request.getRequestedInstances();
        Date now = new Date();

        // Update instance statuses
        for (InventoryInstance instance : instances) {
            if (!(instance.getInventoryInstanceStatus() == InventoryInstanceStatus.PENDING)) {
                // If it was REQUESTED or AVAILABLE, revert back to AVAILABLE
                instance.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                instance.setBookedDate(null);
                instance.setInventoryRequest(null); // optional: unlink request
            } else {
                // If it was PENDING (not yet procured), mark as destroyed
                instance.setInventoryInstanceStatus(InventoryInstanceStatus.DESTROYED);
            }
        }

        List<InventoryInstance> savedInstances = inventoryInstanceRepository.saveAll(instances);

        // Update request status
        request.setApprovalStatus(InventoryApprovalStatus.REJECTED);
        inventoryRequestRepository.save(request);

        // 🔥 **Cancel related procurement orders**
        List<InventoryProcurementOrder> relatedOrders =
                inventoryProcurementOrderRepository.findByInventoryRequestId(requestId);

        for (InventoryProcurementOrder order : relatedOrders) {
            // Only cancel those that are still pending or active
            if (order.getInventoryProcurementStatus() == InventoryProcurementStatus.IN_PROGRESS
                    || order.getInventoryProcurementStatus() == InventoryProcurementStatus.CREATED) {
                order.setInventoryProcurementStatus(InventoryProcurementStatus.CANCELED);
            }
        }
        if (!relatedOrders.isEmpty()) {
            inventoryProcurementOrderRepository.saveAll(relatedOrders);
            logger.info("Canceled {} procurement order(s) linked to request {}", relatedOrders.size(), requestId);
        }

        // Create rejection audit record
        InventoryBookingApproval approval = new InventoryBookingApproval();
        approval.setInventoryRequest(request);
        approval.setApprovalDate(now);
        approval.setApprovedBy(rejectedBy);
        approval.setApprovalStatus(InventoryApprovalStatus.REJECTED);
        approval.setApprovalRemarks(rejectRemarks);
        inventoryBookingApprovalRepository.save(approval);

        // Update availability after rejection
        updateItemAvailability(request.getInventoryItem().getInventoryItemId());

        logger.info("Rejected inventory request {} and reverted {} instance(s)", requestId, savedInstances.size());
        return savedInstances;
    }



    @Override
    @Transactional
    public InventoryRequest requestInstanceByItemId(int itemId, double qty, InventoryRequestSource source, Long sourceId,String requestedBy,String requestRemarks) {

        InventoryItem item = inventoryItemRepository.findByActiveId(itemId);
        if (item == null) {
            throw new ResourceNotFoundException("Inventory item not found with ID: " + itemId);
        }
        return requestInstance(item, qty, source, sourceId,requestedBy, requestRemarks);
    }


    @Override
    @Transactional
    public List<InventoryInstance> addInventory(AddInventoryRequest request) {
        logger.info("Received request to add inventory: {}", request);

        int inventoryItemId = request.getInventoryItemId();
        double addedQty = request.getQuantity();
        double costPerUnit = request.getCostPerUnit();
        long referenceId = request.getReferenceId();
        String createdBy = request.getCreatedBy();
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

        List<InventoryInstance> resultInstances = new ArrayList<>();
        Date now = new Date();

        double finalCost = costPerUnit > 0 ? costPerUnit :
                dbItem.getProductFinanceSettings().getStandardCost() != null ? dbItem.getProductFinanceSettings().getStandardCost() : 0;
        double finalPrice = dbItem.getProductFinanceSettings().getStandardCost() != null ? dbItem.getProductFinanceSettings().getStandardCost() : 0;

        try {
            // =========================================
            // STEP 1: FILL EXISTING PENDING INSTANCES
            // =========================================
            List<InventoryInstance> pendingInstances = inventoryInstanceRepository
                    .inventoryInstanceByStatus(
                            inventoryItemId,
                            InventoryInstanceStatus.PENDING.name(),
                            addedQty // qty parameter, as per your existing query
                    );

            double remainingQty = addedQty;

            if (!pendingInstances.isEmpty()) {
                logger.info("Found {} pending instances to fill", pendingInstances.size());

                for (InventoryInstance inst : pendingInstances) {
                    if (isUnitByNos(dbItem)) {
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                        inst.setEntryDate(now);
                        inst.setCostPerUnit(finalCost);
                        inst.setSellPricePerUnit(finalPrice);
                        resultInstances.add(inst);
                        remainingQty -= 1;
                        if (remainingQty <= 0) break;
                    } else {
                        // For quantity-based instance, assume full quantity is pending
                        double canFill = Math.min(inst.getQuantity(), remainingQty);
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                        inst.setEntryDate(now);
                        inst.setCostPerUnit(finalCost);
                        inst.setSellPricePerUnit(finalPrice);
                        resultInstances.add(inst);
                        remainingQty -= canFill;
                        if (remainingQty <= 0) break;
                    }
                }

                inventoryInstanceRepository.saveAll(pendingInstances);
                logger.info("Updated {} pending instances to AVAILABLE", pendingInstances.size());
            }

            // =========================================
            // STEP 2: CREATE NEW INSTANCES IF STILL NEEDED
            // =========================================
            if (remainingQty > 0) {
                logger.info("Still need to add {} new units", remainingQty);

                if (isUnitByNos(dbItem)) {
                    int unitCount = (int) remainingQty;
                    for (int i = 0; i < unitCount; i++) {
                        InventoryInstance inst = new InventoryInstance();
                        inst.setInventoryItem(dbItem);
                        inst.setEntryDate(now);
                        inst.setQuantity(1);
                        inst.setCostPerUnit(finalCost);
                        inst.setSellPricePerUnit(finalPrice);
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                        resultInstances.add(inst);
                    }
                } else {
                    InventoryInstance inst = new InventoryInstance();
                    inst.setInventoryItem(dbItem);
                    inst.setEntryDate(now);
                    inst.setQuantity(remainingQty);
                    inst.setCostPerUnit(finalCost);
                    inst.setSellPricePerUnit(finalPrice);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    resultInstances.add(inst);
                }

                // save only the newly created instances (not the already updated pending ones)
                List<InventoryInstance> newInstances = resultInstances.stream()
                        .filter(i -> i.getInventoryItem().getInventoryItemId() == 0) // id=0 means new, adjust if you use different id logic
                        .toList();
                if (!newInstances.isEmpty()) {
                    inventoryInstanceRepository.saveAll(newInstances);
                }
            }

            // =========================================
            // STEP 3: Create procurement order record
            // =========================================

            InventoryProcurementOrder inventoryProcurementOrder = new InventoryProcurementOrder();
            inventoryProcurementOrder.setInventoryItem(dbItem);
            inventoryProcurementOrder.setPendingInventoryList(resultInstances);
            inventoryProcurementOrder.setProcurementDecision(procurementDecision);
            inventoryProcurementOrder.setOrderId(referenceId);
            inventoryProcurementOrder.setCreatedBy(createdBy);
            inventoryProcurementOrder.setInventoryProcurementStatus(InventoryProcurementStatus.COMPLETED);
            inventoryProcurementOrderRepository.save(inventoryProcurementOrder);

            // =========================================
            // STEP 4: Update item availability
            // =========================================
            updateItemAvailability(dbItem.getInventoryItemId());
            logger.info("Updated item availability for item ID {}", inventoryItemId);

            logger.info("Successfully processed total {} instances (updated + new) for item ID {}",
                    resultInstances.size(), inventoryItemId);

            return resultInstances;

        } catch (Exception ex) {
            logger.error("Error occurred while adding inventory instances", ex);
            throw new RuntimeException("Error occurred while adding inventory: " + ex.getMessage(), ex);
        }
    }



    public Page<InventoryRequestGroupDTO> getGroupedRequests(
            int page, int size,
            String itemCode, String itemName,
            InventoryRequestSource source,
            InventoryApprovalStatus approvalStatus,
            Long referenceId
    ) {
        // Step 1: Fetch all requests and apply filters
        List<InventoryRequest> filteredRequests = inventoryRequestRepository.findAll().stream()
                .filter(req -> referenceId == null || req.getSourceId() != null && req.getSourceId().toString().contains(referenceId.toString()))
                .filter(req -> source == null || req.getRequestSource() == source)
                .filter(req -> approvalStatus == null || req.getApprovalStatus() == approvalStatus)
                .filter(req -> req.getInventoryItem() != null &&
                        (itemCode == null || req.getInventoryItem().getItemCode().toLowerCase().contains(itemCode.toLowerCase())) &&
                        (itemName == null || req.getInventoryItem().getName().toLowerCase().contains(itemName.toLowerCase()))
                )
                .toList();

        // Step 2: Map to DTOs
        List<InventoryRequestGroupDTO> dtoList = filteredRequests.stream()
                .map(req -> {
                    InventoryItem item = req.getInventoryItem();
                    double totalQty = req.getRequestedInstances().stream()
                            .mapToDouble(InventoryInstance::getQuantity)
                            .sum();
                    double requestQty = req.getRequestedInstances().stream()
                            .filter(inst -> inst.getInventoryInstanceStatus() == InventoryInstanceStatus.REQUESTED)
                            .count();
                    return new InventoryRequestGroupDTO(
                            req.getId(),
                            req.getSourceId(),
                            item.getInventoryItemId(),
                            item.getItemCode(),
                            item.getName(),
                            totalQty,
                            requestQty,
                            totalQty-requestQty,
                            req.getRequestedDate(),
                            req.getRequestSource(),
                            req.getRequestedBy(),
                            req.getApprovalStatus()
                    );
                }).toList();

        // Step 3: Pagination
        int total = dtoList.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<InventoryRequestGroupDTO> pageContent = dtoList.subList(fromIndex, toIndex);

        return new PageImpl<>(pageContent, PageRequest.of(page, size), total);
    }

    @Override
    public List<InventoryInstance> getRequestedInstancesByReferenceAndItem(Long referenceId) {

        List<InventoryInstance>  instanceList = inventoryRequestRepository.getReferenceById(referenceId).getRequestedInstances();
        return instanceList;
    }


    @Transactional
    @Override
    public void updateProcurementStatus(Long procurementOrderId, InventoryProcurementStatus newStatus) {
        InventoryProcurementOrder order = inventoryProcurementOrderRepository.findById(procurementOrderId)
                .orElseThrow(() -> new RuntimeException("Procurement order not found"));

        // Update the status
        order.setInventoryProcurementStatus(newStatus);

        if (newStatus == InventoryProcurementStatus.COMPLETED) {
            boolean hasRequest = order.getInventoryRequest() != null;

            for (InventoryInstance instance : order.getPendingInventoryList()) {
                instance.setInventoryInstanceStatus(hasRequest
                        ? InventoryInstanceStatus.REQUESTED
                        : InventoryInstanceStatus.AVAILABLE);
            }
        }

        inventoryProcurementOrderRepository.save(order);
    }


    @Transactional
    @Override
    public Page<InventoryProcurementOrderDTO> getProcurementOrders(
            int page, int size,
            InventoryProcurementStatus status,
            Long inventoryItemId,
            String createdBy
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "creationDate"));

        List<InventoryProcurementOrder> allOrders = inventoryProcurementOrderRepository.findAll();

        List<InventoryProcurementOrderDTO> filtered = allOrders.stream()
                .filter(order -> status == null || order.getInventoryProcurementStatus() == status)
                .filter(order -> inventoryItemId == null ||
                        (order.getInventoryItem() != null &&
                                order.getInventoryItem().getInventoryItemId() == inventoryItemId))
                .filter(order -> createdBy == null ||
                        (order.getCreatedBy() != null &&
                                order.getCreatedBy().equalsIgnoreCase(createdBy)))
                .map(order -> new InventoryProcurementOrderDTO(
                        order.getId(),
                        order.getInventoryItem() != null ? order.getInventoryItem().getItemCode() : null,
                        order.getInventoryItem() != null ? order.getInventoryItem().getName() : null,
                        order.getInventoryProcurementStatus(),
                        order.getProcurementDecision(),
                        order.getPendingInventoryList() != null ? order.getPendingInventoryList().size() : 0,
                        order.getInventoryRequest() != null ? order.getInventoryRequest().getId() : null,
                        order.getCreatedBy(),
                        order.getCreationDate()
                ))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<InventoryProcurementOrderDTO> pageContent = filtered.subList(fromIndex, toIndex);

        return new PageImpl<>(pageContent, pageable, total);
    }

    @Override
    @Transactional
    public List<InventoryInstance> addInventoryToExistingProcurement(AddInventoryRequest request, long procurementOrderId) {
        logger.info("Received request to add inventory to existing procurement {}: {}", procurementOrderId, request);

        int inventoryItemId = request.getInventoryItemId();
        double addedQty = request.getQuantity();
        double costPerUnit = request.getCostPerUnit();

        if (addedQty <= 0) {
            logger.warn("Attempted to add invalid quantity: {}", addedQty);
            throw new IllegalArgumentException("Added quantity must be greater than zero.");
        }

        // ✅ Fetch existing procurement order
        InventoryProcurementOrder procurementOrder = inventoryProcurementOrderRepository.findById(procurementOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Procurement order not found with ID: " + procurementOrderId));

        InventoryItem dbItem = inventoryItemRepository.findByActiveId(inventoryItemId);
        if (dbItem == null) {
            logger.error("Inventory item not found with id: {}", inventoryItemId);
            throw new ResourceNotFoundException("Inventory item not found with ID: " + inventoryItemId);
        }

        logger.info("Inventory item found: {} - {}", dbItem.getInventoryItemId(), dbItem.getName());

        List<InventoryInstance> resultInstances = new ArrayList<>();
        Date now = new Date();

        double finalCost = costPerUnit > 0 ? costPerUnit :
                dbItem.getProductFinanceSettings().getStandardCost() != null ? dbItem.getProductFinanceSettings().getStandardCost(): 0;
        double finalPrice = dbItem.getProductFinanceSettings().getStandardCost() != null ? dbItem.getProductFinanceSettings().getStandardCost() : 0;

        try {
            // =========================================
            // STEP 1: FILL EXISTING PENDING INSTANCES
            // =========================================
            List<InventoryInstance> pendingInstances = inventoryInstanceRepository
                    .inventoryInstanceByStatus(
                            inventoryItemId,
                            InventoryInstanceStatus.PENDING.name(),
                            addedQty
                    );

            double remainingQty = addedQty;

            if (!pendingInstances.isEmpty()) {
                logger.info("Found {} pending instances to fill", pendingInstances.size());

                for (InventoryInstance inst : pendingInstances) {
                    if (isUnitByNos(dbItem)) {
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                        inst.setEntryDate(now);
                        inst.setCostPerUnit(finalCost);
                        inst.setSellPricePerUnit(finalPrice);
                        resultInstances.add(inst);
                        remainingQty -= 1;
                        if (remainingQty <= 0) break;
                    } else {
                        double canFill = Math.min(inst.getQuantity(), remainingQty);
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                        inst.setEntryDate(now);
                        inst.setCostPerUnit(finalCost);
                        inst.setSellPricePerUnit(finalPrice);
                        resultInstances.add(inst);
                        remainingQty -= canFill;
                        if (remainingQty <= 0) break;
                    }
                }

                inventoryInstanceRepository.saveAll(pendingInstances);
                logger.info("Updated {} pending instances to AVAILABLE", pendingInstances.size());
            }

            // =========================================
            // STEP 2: CREATE NEW INSTANCES IF STILL NEEDED
            // =========================================
            if (remainingQty > 0) {
                logger.info("Still need to add {} new units", remainingQty);

                if (isUnitByNos(dbItem)) {
                    int unitCount = (int) remainingQty;
                    for (int i = 0; i < unitCount; i++) {
                        InventoryInstance inst = new InventoryInstance();
                        inst.setInventoryItem(dbItem);
                        inst.setEntryDate(now);
                        inst.setQuantity(1);
                        inst.setCostPerUnit(finalCost);
                        inst.setSellPricePerUnit(finalPrice);
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                        resultInstances.add(inst);
                    }
                } else {
                    InventoryInstance inst = new InventoryInstance();
                    inst.setInventoryItem(dbItem);
                    inst.setEntryDate(now);
                    inst.setQuantity(remainingQty);
                    inst.setCostPerUnit(finalCost);
                    inst.setSellPricePerUnit(finalPrice);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    resultInstances.add(inst);
                }

                // save new instances
                List<InventoryInstance> newInstances = resultInstances.stream()
                        .filter(i -> i.getInventoryItem().getInventoryItemId() == 0)
                        .toList();
                if (!newInstances.isEmpty()) {
                    inventoryInstanceRepository.saveAll(newInstances);
                }
            }

            // =========================================
            // STEP 3: Update existing procurement order
            // =========================================
            if (procurementOrder.getPendingInventoryList() == null) {
                procurementOrder.setPendingInventoryList(new ArrayList<>());
            }
            procurementOrder.getPendingInventoryList().addAll(resultInstances);

            inventoryProcurementOrderRepository.save(procurementOrder);

            // =========================================
            // STEP 4: Update item availability
            // =========================================
            updateItemAvailability(dbItem.getInventoryItemId());
            logger.info("Updated item availability for item ID {}", inventoryItemId);

            logger.info("Successfully added {} instances to procurement order {}", resultInstances.size(), procurementOrderId);
            updateItemAvailability(dbItem.getInventoryItemId());
            return resultInstances;

        } catch (Exception ex) {
            logger.error("Error occurred while adding inventory instances to existing procurement", ex);
            throw new RuntimeException("Error occurred while adding inventory: " + ex.getMessage(), ex);
        }
    }


    @Transactional
    @Override
    public InventoryProcurementOrderDTO completeProcurementOrder(Long orderId, String completedBy) {
        // Fetch the procurement order
        InventoryProcurementOrder order = inventoryProcurementOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Procurement order not found with ID: " + orderId));

        // Check if already completed
        if (order.getInventoryProcurementStatus() == InventoryProcurementStatus.COMPLETED) {
            throw new IllegalStateException("Procurement order is already completed.");
        }

        Date now = new Date();

        // ✅ Update instances status
        List<InventoryInstance> pendingInstances = order.getPendingInventoryList();
        if (pendingInstances != null && !pendingInstances.isEmpty()) {
            for (InventoryInstance inst : pendingInstances) {
                if (inst.getInventoryInstanceStatus() == InventoryInstanceStatus.PENDING) {
                    if (inst.getInventoryRequest() != null) {
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                        inst.setBookedDate(now);
                    } else {
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                        inst.setBookedDate(null);
                    }
                }
            }
            inventoryInstanceRepository.saveAll(pendingInstances);

            // Update item availability for the item type
            updateItemAvailability(pendingInstances.get(0).getInventoryItem().getInventoryItemId());
        }

        // ✅ Update order status
        order.setInventoryProcurementStatus(InventoryProcurementStatus.COMPLETED);
        InventoryProcurementOrder savedOrder = inventoryProcurementOrderRepository.save(order);

        // ✅ Manually build and return DTO
        InventoryProcurementOrderDTO dto = new InventoryProcurementOrderDTO();
        dto.setId(savedOrder.getId());
        dto.setItemCode(savedOrder.getInventoryItem() != null ? savedOrder.getInventoryItem().getItemCode() : null);
        dto.setItemName(savedOrder.getInventoryItem() != null ? savedOrder.getInventoryItem().getName() : null);
        dto.setStatus(savedOrder.getInventoryProcurementStatus());
        dto.setDecision(savedOrder.getProcurementDecision());
        dto.setTotalInstances(
                savedOrder.getPendingInventoryList() != null ? savedOrder.getPendingInventoryList().size() : 0
        );
        dto.setRequestId(savedOrder.getInventoryRequest() != null ? savedOrder.getInventoryRequest().getId() : null);
        dto.setCreatedBy(savedOrder.getCreatedBy());

        return dto;
    }




}

