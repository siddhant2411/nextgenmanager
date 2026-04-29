package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.*;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryBookingApprovalRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryProcurementOrderRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.bom.service.BomServiceException;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        try {
            InventoryItem item = inventoryItemRepository.findByActiveId(itemId);
            if (item == null) {
                logger.warn("Attempted to update availability for non-existent item ID: {}", itemId);
                return;
            }

            double availableQty;
            if (item.getUom() == UOM.NOS) {
                availableQty = inventoryInstanceRepository.countAvailableInInventory(itemId, InventoryInstanceStatus.AVAILABLE.name());
            } else {
                availableQty = inventoryInstanceRepository.getTotalQuantityForNonNOSItem(itemId);
            }

            item.getProductInventorySettings().setAvailableQuantity(availableQty);
            inventoryItemRepository.save(item);
            logger.debug("Updated availability for item ID {}: {}", itemId, availableQty);
        } catch (Exception e) {
            logger.error("Error in updateItemAvailability for item ID {}: {}", itemId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void revertInventoryInstances(List<InventoryInstance> instances) {
        try {
            List<InventoryInstance> inventoryInstanceList = new ArrayList<>();
            for(InventoryInstance instance: instances){
                InventoryInstance inst = inventoryInstanceRepository.getReferenceById(instance.getId());
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                inst.setConsumed(false);
                inst.setBookedDate(null);
                inst.setConsumeDate(null);
                inventoryInstanceList.add(inst);
            }
            inventoryInstanceRepository.saveAll(inventoryInstanceList);
        } catch (Exception e) {
            logger.error("Error in revertInventoryInstances: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public List<InventoryInstance> createInstances(InventoryItem item, double qty, InventoryInstance template) {
        try {
            InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
            if (dbItem == null) throw new ResourceNotFoundException("Inventory item not found");

            List<InventoryInstance> instances = new ArrayList<>();
            Date now = new Date();
            BigDecimal standardCost = item.getProductFinanceSettings() != null
                    ? BigDecimal.valueOf(item.getProductFinanceSettings().getStandardCost())
                    : BigDecimal.ZERO;

            if (isUnitByNos(dbItem)) {
                for (int i = 0; i < (int) qty; i++) {
                    InventoryInstance inst = new InventoryInstance();
                    inst.setInventoryItem(dbItem);
                    inst.setEntryDate(now);
                    inst.setQuantity(BigDecimal.ONE);
                    inst.setCostPerUnit(template.getCostPerUnit() != null ? template.getCostPerUnit() : standardCost);
                    inst.setSellPricePerUnit(template.getSellPricePerUnit() != null ? template.getSellPricePerUnit() : standardCost);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    instances.add(inst);
                }
            } else {
                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(dbItem);
                inst.setEntryDate(now);
                inst.setQuantity(BigDecimal.valueOf(qty));
                inst.setCostPerUnit(template.getCostPerUnit() != null ? template.getCostPerUnit() : standardCost);
                inst.setSellPricePerUnit(template.getSellPricePerUnit() != null ? template.getSellPricePerUnit() : standardCost);
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                instances.add(inst);
            }
            List<InventoryInstance> saved = inventoryInstanceRepository.saveAll(instances);
            updateItemAvailability(dbItem.getInventoryItemId());
            return saved;
        } catch (Exception e) {
            logger.error("Error in createInstances for item {}: {}", item.getInventoryItemId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public List<InventoryInstance> bookInventoryInstance(InventoryItem item, double qty) {
        try {
            InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
            if (dbItem == null) throw new ResourceNotFoundException("Inventory item not found");

            List<InventoryInstance> booked = new ArrayList<>();
            Date now = new Date();

            if (isUnitByNos(dbItem)) {
                List<InventoryInstance> available = inventoryInstanceRepository.getItemsToBook(dbItem.getInventoryItemId(), Pageable.ofSize((int) qty));
                for (InventoryInstance inst : available) {
                    inst.setBookedDate(now);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.BOOKED);
                    booked.add(inst);
                }
            } else {
                InventoryInstance inst = inventoryInstanceRepository.findLatestInventoryInstance(dbItem.getInventoryItemId(), qty);
                if (inst != null) {
                    inst.setBookedDate(now);
                    inst.setQuantity(BigDecimal.valueOf(qty));
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.BOOKED);
                    booked.add(inst);
                }
            }
            updateItemAvailability(item.getInventoryItemId());
            return inventoryInstanceRepository.saveAll(booked);
        } catch (Exception e) {
            logger.error("Error in bookInventoryInstance for item {}: {}", item.getInventoryItemId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public List<InventoryInstance> consumeInventoryInstance(InventoryItem item, double qty, Long requestId) {
        try {
            InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
            if (dbItem == null) throw new ResourceNotFoundException("Inventory item not found");

            InventoryRequest request = inventoryRequestRepository.getReferenceById(requestId);
            if (request.getRequestedInstances() == null || request.getRequestedInstances().isEmpty()) {
                throw new IllegalArgumentException("Invalid inventory request or no instances provided.");
            }

            List<InventoryInstance> consumed = new ArrayList<>();
            Date now = new Date();

            if (isUnitByNos(dbItem)) {
                List<InventoryInstance> available = request.getRequestedInstances();
                int count = 0;
                for (InventoryInstance inst : available) {
                    if (count >= (int) qty) break;
                    if (inst.getInventoryItem().equals(dbItem)) {
                        inst.setConsumed(true);
                        inst.setConsumeDate(now);
                        inst.setQuantity(BigDecimal.ZERO);
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.CONSUMED);
                        consumed.add(inst);
                        count++;
                    }
                }
            } else {
                List<InventoryInstance> instanceList = request.getRequestedInstances();
                double remainingQty = qty;
                for (InventoryInstance instance : instanceList) {
                    if (!instance.getInventoryItem().equals(dbItem)) continue;
                    if (remainingQty <= 0) break;

                    BigDecimal availableQty = instance.getQuantity();
                    BigDecimal toConsume = BigDecimal.valueOf(remainingQty).min(availableQty);

                    instance.setConsumeDate(now);
                    BigDecimal newQty = availableQty.subtract(toConsume);
                    instance.setQuantity(newQty);
                    instance.setConsumed(newQty.compareTo(BigDecimal.ZERO) == 0);
                    if (newQty.compareTo(BigDecimal.ZERO) == 0) {
                        instance.setInventoryInstanceStatus(InventoryInstanceStatus.CONSUMED);
                    }
                    consumed.add(instance);
                    remainingQty -= toConsume.doubleValue();
                }
            }

            inventoryInstanceRepository.saveAll(consumed);
            updateItemAvailability(dbItem.getInventoryItemId());
            return consumed;
        } catch (Exception e) {
            logger.error("Error in consumeInventoryInstance for item {} (RequestId: {}): {}", item.getInventoryItemId(), requestId, e.getMessage(), e);
            throw e;
        }
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

        try {
            InventoryItem dbItem = inventoryItemRepository.findByActiveId(item.getInventoryItemId());
            if (dbItem == null) throw new ResourceNotFoundException("Inventory item not found");
            if (qty <= 0) throw new IllegalArgumentException("Requested quantity must be greater than zero.");

            Date now = new Date();
            List<InventoryInstance> resultInstances = new ArrayList<>();

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

            List<InventoryInstance> availableInstances =
                    inventoryInstanceRepository.inventoryInstanceByStatus(
                            dbItem.getInventoryItemId(),
                            InventoryInstanceStatus.AVAILABLE.name(),
                            qty);

            if (isUnitByNos(dbItem)) {
                int requestedUnits = (int) qty;
                int fulfilled = 0;
                for (InventoryInstance inst : availableInstances) {
                    if (fulfilled >= requestedUnits) break;
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                    inst.setBookedDate(now);
                    inst.setInventoryRequest(request);
                    resultInstances.add(inst);
                    fulfilled++;
                }
                int remaining = requestedUnits - fulfilled;
                for (int i = 0; i < remaining; i++) {
                    InventoryInstance inst = new InventoryInstance();
                    inst.setInventoryItem(dbItem);
                    inst.setEntryDate(now);
                    inst.setQuantity(BigDecimal.ONE);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.PENDING);
                    inst.setInventoryRequest(request);
                    resultInstances.add(inst);
                }
            } else {
                BigDecimal availableQty = availableInstances.stream()
                        .map(InventoryInstance::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal requestedQty = BigDecimal.valueOf(qty);

                if (availableQty.compareTo(requestedQty) >= 0) {
                    BigDecimal qtyToBook = requestedQty;
                    for (InventoryInstance inst : availableInstances) {
                        if (qtyToBook.compareTo(BigDecimal.ZERO) <= 0) break;
                        BigDecimal instQty = inst.getQuantity();
                        BigDecimal usedQty = instQty.min(qtyToBook);
                        inst.setQuantity(usedQty);
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                        inst.setBookedDate(now);
                        inst.setInventoryRequest(request);
                        resultInstances.add(inst);
                        qtyToBook = qtyToBook.subtract(usedQty);
                    }
                } else {
                    BigDecimal qtyToRequest = requestedQty.subtract(availableQty);
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

            List<InventoryInstance> savedInstances = inventoryInstanceRepository.saveAll(resultInstances);
            request.setRequestedInstances(savedInstances);
            InventoryRequest savedRequest = inventoryRequestRepository.save(request);

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
            }

            updateItemAvailability(dbItem.getInventoryItemId());
            return savedRequest;
        } catch (Exception e) {
            logger.error("Error in requestInstance for item {}: {}", item.getInventoryItemId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public InventoryInstance updateInventoryInstance(InventoryInstance updated) {
        try {
            if (updated == null || updated.getId() == null) throw new IllegalArgumentException("Inventory instance or ID must not be null");
            InventoryInstance existing = getInventoryInstanceById(updated.getId());
            boolean modified = false;

            if (updated.getQuantity() != null && updated.getQuantity().compareTo(existing.getQuantity()) != 0) {
                existing.setQuantity(updated.getQuantity());
                modified = true;
            }
            if (updated.getCostPerUnit() != null && !updated.getCostPerUnit().equals(existing.getCostPerUnit())) {
                existing.setCostPerUnit(updated.getCostPerUnit());
                modified = true;
            }
            if (updated.getSellPricePerUnit() != null && !updated.getSellPricePerUnit().equals(existing.getSellPricePerUnit())) {
                existing.setSellPricePerUnit(updated.getSellPricePerUnit());
                modified = true;
            }
            if (updated.getConsumeDate() != null && !updated.getConsumeDate().equals(existing.getConsumeDate())) {
                existing.setConsumeDate(updated.getConsumeDate());
                modified = true;
            }
            if (updated.isConsumed() != existing.isConsumed()) {
                existing.setConsumed(updated.isConsumed());
                modified = true;
            }
            if (updated.getDeletedDate() != null && existing.getDeletedDate() == null) {
                existing.setDeletedDate(updated.getDeletedDate());
                modified = true;
            }

            if (modified) {
                return inventoryInstanceRepository.save(existing);
            }
            return existing;
        } catch (Exception e) {
            logger.error("Error in updateInventoryInstance for ID {}: {}", updated != null ? updated.getId() : "null", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void deleteInventoryInstance(long id) {
        try {
            InventoryInstance inventoryInstance = getInventoryInstanceById(id);
            inventoryInstance.setDeletedDate(new Date());
            inventoryInstanceRepository.save(inventoryInstance);
            updateItemAvailability(inventoryInstance.getInventoryItem().getInventoryItemId());
        } catch (Exception e) {
            logger.error("Error while deleting Inventory Instance with ID {}: {}", id, e.getMessage());
            throw new BomServiceException("Failed to delete Inventory Instance", e);
        }
    }

    @Override
    public List<InventoryInstance> getAllInventoryInstances(int page, int size, String sortBy, String sortDir, String query) {
        try {
            Pageable pageable = PageRequest.of(page, size, sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());
            return inventoryInstanceRepository.findAll(pageable).getContent();
        } catch (Exception e) {
            logger.error("Error in getAllInventoryInstances: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Page<InventoryPresentDTO> getPresentInventoryInstances(int page, int size, String sortBy, String sortDir,
                                                                   String queryItemCode, String queryItemName, String queryHsnCode,
                                                                   Double totalQuantityCondition, String filterType, UOM queryUOM,
                                                                   ItemType itemType) {
        try {
            Pageable pageable = PageRequest.of(page, size, sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());
            Integer uom = (queryUOM != null) ? queryUOM.ordinal() : null;
            Integer itemTypeValue = (itemType != null) ? itemType.ordinal() : null;

            Page<Object[]> inventoryListWithCount = inventoryInstanceRepository.getItemsForInventoryPage(
                    pageable, queryItemCode, queryItemName, queryHsnCode, totalQuantityCondition, filterType, uom, itemTypeValue);

            return inventoryListWithCount.map(record -> {
                int inventoryItemRef = ((Number) record[0]).intValue();
                double totalQuantity = ((Number) record[6]).doubleValue();
                double totalCost = ((Number) record[7]).doubleValue();
                InventoryItem inventoryItem = inventoryItemRepository.findByActiveId(inventoryItemRef);
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
            });
        } catch (Exception e) {
            logger.error("Error in getPresentInventoryInstances: {}", e.getMessage(), e);
            throw e;
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
            Integer uom = (queryUOM != null) ? queryUOM.ordinal() : null;
            Integer itemTypeValue = (itemType != null) ? itemType.ordinal() : null;

            List<InventoryInstance> allInstances = inventoryInstanceRepository.getAllActiveInstancesFiltered(
                    queryItemCode, queryItemName, queryHsnCode, uom, itemTypeValue);

            Map<Integer, List<InventoryInstance>> groupedByItem = allInstances.stream()
                    .filter(inst -> inst.getInventoryItem() != null)
                    .collect(Collectors.groupingBy(inst -> inst.getInventoryItem().getInventoryItemId()));

            List<GroupedInventoryItem> groupedList = groupedByItem.values().stream()
                    .filter(instances -> {
                        BigDecimal sum = instances.stream()
                                .map(InventoryInstance::getQuantity)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (filterType == null || totalQuantityCondition == null) return true;
                        BigDecimal targetQuantity = BigDecimal.valueOf(totalQuantityCondition);
                        return switch (filterType) {
                            case "=" -> sum.compareTo(targetQuantity) == 0;
                            case ">" -> sum.compareTo(targetQuantity) > 0;
                            case "<" -> sum.compareTo(targetQuantity) < 0;
                            default -> true;
                        };
                    })
                    .map(instances -> new GroupedInventoryItem(instances.get(0).getInventoryItem(), instances))
                    .toList();

            Pageable pageable = PageRequest.of(page, size);
            int start = Math.min((int) pageable.getOffset(), groupedList.size());
            int end = Math.min(start + pageable.getPageSize(), groupedList.size());
            return new PageImpl<>(groupedList.subList(start, end), pageable, groupedList.size());
        } catch (Exception e) {
            logger.error("Error in getGroupedInventoryInstances: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<InventoryInstance> getInventoryInstanceByItemId(int inventoryItemId, int page, int size, String sortBy, String sortDir, String query) {
        return null;
    }

    @Override
    public InventoryInstance getInventoryInstanceById(long id) {
        try {
            return inventoryInstanceRepository.findById(id)
                    .filter(inst -> inst.getDeletedDate() == null)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory Instance not found with ID: " + id));
        } catch (Exception e) {
            logger.error("Error in getInventoryInstanceById for ID {}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Map<String, Object> getInventorySummary() {
        try {
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalItems", inventoryItemRepository.countByDeletedDateIsNull());
            summary.put("available", inventoryInstanceRepository.sumAvailableQuantity());
            summary.put("requested", inventoryInstanceRepository.countByInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED.name()));
            summary.put("booked", inventoryInstanceRepository.countByInventoryInstanceStatus(InventoryInstanceStatus.BOOKED.name()));
            summary.put("consumed", inventoryInstanceRepository.countByInventoryInstanceStatus(InventoryInstanceStatus.CONSUMED.name()));
            summary.put("totalInventoryValue", inventoryInstanceRepository.countSumOfInventoryValue());
            return summary;
        } catch (Exception e) {
            logger.error("Error in getInventorySummary: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    @Override
    public List<InventoryInstance> approveInventoryRequest(Long requestId, String approvedBy, String approveRemarks) {
        try {
            InventoryRequest request = inventoryRequestRepository.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory Request not found"));
                    
            if (request.getRequestSource() == InventoryRequestSource.WORK_ORDER) {
                throw new IllegalStateException("Work Order Material Requests must be approved via the Work Order Production flow.");
            }

            List<InventoryInstance> instances = request.getRequestedInstances();
            if (instances.stream().anyMatch(i -> i.getInventoryInstanceStatus() == InventoryInstanceStatus.PENDING)) {
                throw new IllegalStateException("Cannot approve: Some instances are pending.");
            }

            Date now = new Date();
            for (InventoryInstance instance : instances) {
                instance.setInventoryInstanceStatus(InventoryInstanceStatus.BOOKED);
                instance.setBookedDate(now);
            }
            List<InventoryInstance> saved = inventoryInstanceRepository.saveAll(instances);
            request.setApprovalStatus(InventoryApprovalStatus.APPROVED);
            inventoryRequestRepository.save(request);

            InventoryBookingApproval approval = new InventoryBookingApproval();
            approval.setInventoryRequest(request);
            approval.setApprovalDate(now);
            approval.setApprovedBy(approvedBy);
            approval.setApprovalStatus(InventoryApprovalStatus.APPROVED);
            approval.setApprovalRemarks(approveRemarks);
            inventoryBookingApprovalRepository.save(approval);
            updateItemAvailability(request.getInventoryItem().getInventoryItemId());
            return saved;
        } catch (Exception e) {
            logger.error("Error in approveInventoryRequest for request {}: {}", requestId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    @Override
    public List<InventoryInstance> rejectInventoryRequest(Long requestId, String rejectedBy, String rejectRemarks) {
        try {
            InventoryRequest request = inventoryRequestRepository.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory Request not found"));

            if (request.getRequestSource() == InventoryRequestSource.WORK_ORDER) {
                throw new IllegalStateException("Work Order Material Requests must be rejected via the Work Order Production flow.");
            }

            List<InventoryInstance> instances = request.getRequestedInstances();
            Date now = new Date();
            for (InventoryInstance instance : instances) {
                if (instance.getInventoryInstanceStatus() != InventoryInstanceStatus.PENDING) {
                    instance.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    instance.setBookedDate(null);
                    instance.setInventoryRequest(null);
                } else {
                    instance.setInventoryInstanceStatus(InventoryInstanceStatus.DESTROYED);
                }
            }
            List<InventoryInstance> saved = inventoryInstanceRepository.saveAll(instances);
            request.setApprovalStatus(InventoryApprovalStatus.REJECTED);
            inventoryRequestRepository.save(request);

            List<InventoryProcurementOrder> relatedOrders = inventoryProcurementOrderRepository.findByInventoryRequestId(requestId);
            for (InventoryProcurementOrder order : relatedOrders) {
                if (order.getInventoryProcurementStatus() == InventoryProcurementStatus.IN_PROGRESS || order.getInventoryProcurementStatus() == InventoryProcurementStatus.CREATED) {
                    order.setInventoryProcurementStatus(InventoryProcurementStatus.CANCELED);
                }
            }
            inventoryProcurementOrderRepository.saveAll(relatedOrders);

            InventoryBookingApproval approval = new InventoryBookingApproval();
            approval.setInventoryRequest(request);
            approval.setApprovalDate(now);
            approval.setApprovedBy(rejectedBy);
            approval.setApprovalStatus(InventoryApprovalStatus.REJECTED);
            approval.setApprovalRemarks(rejectRemarks);
            inventoryBookingApprovalRepository.save(approval);
            updateItemAvailability(request.getInventoryItem().getInventoryItemId());
            return saved;
        } catch (Exception e) {
            logger.error("Error in rejectInventoryRequest for request {}: {}", requestId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public InventoryRequest requestInstanceByItemId(int itemId, double qty, InventoryRequestSource source, Long sourceId, String requestedBy, String requestRemarks) {
        try {
            InventoryItem item = inventoryItemRepository.findByActiveId(itemId);
            if (item == null) throw new ResourceNotFoundException("Inventory item not found");
            return requestInstance(item, qty, source, sourceId, requestedBy, requestRemarks);
        } catch (Exception e) {
            logger.error("Error in requestInstanceByItemId for item {}: {}", itemId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public List<InventoryInstance> addInventory(AddInventoryRequest request) {
        try {
            int inventoryItemId = request.getInventoryItemId();
            BigDecimal addedQty = BigDecimal.valueOf(request.getQuantity());
            BigDecimal costPerUnit = BigDecimal.valueOf(request.getCostPerUnit());
            InventoryItem dbItem = inventoryItemRepository.findByActiveId(inventoryItemId);
            if (dbItem == null) throw new ResourceNotFoundException("Inventory item not found");

            List<InventoryInstance> resultInstances = new ArrayList<>();
            Date now = new Date();
            BigDecimal standardCost = Optional.ofNullable(dbItem.getProductFinanceSettings().getStandardCost()).map(BigDecimal::valueOf).orElse(BigDecimal.ZERO);
            BigDecimal finalCost = costPerUnit.compareTo(BigDecimal.ZERO) > 0 ? costPerUnit : standardCost;

            List<InventoryInstance> pendingInstances = inventoryInstanceRepository.inventoryInstanceByStatus(inventoryItemId, InventoryInstanceStatus.PENDING.name(), addedQty.doubleValue());
            BigDecimal remainingQty = addedQty;

            for (InventoryInstance inst : pendingInstances) {
                if (isUnitByNos(dbItem)) {
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    inst.setEntryDate(now);
                    inst.setCostPerUnit(finalCost);
                    inst.setSellPricePerUnit(standardCost);
                    resultInstances.add(inst);
                    remainingQty = remainingQty.subtract(BigDecimal.ONE);
                    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                } else {
                    BigDecimal canFill = inst.getQuantity().min(remainingQty);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    inst.setEntryDate(now);
                    inst.setCostPerUnit(finalCost);
                    inst.setSellPricePerUnit(standardCost);
                    resultInstances.add(inst);
                    remainingQty = remainingQty.subtract(canFill);
                    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                }
            }
            inventoryInstanceRepository.saveAll(pendingInstances);

            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                if (isUnitByNos(dbItem)) {
                    int unitCount = remainingQty.intValue();
                    for (int i = 0; i < unitCount; i++) {
                        InventoryInstance inst = new InventoryInstance();
                        inst.setInventoryItem(dbItem);
                        inst.setEntryDate(now);
                        inst.setQuantity(BigDecimal.ONE);
                        inst.setCostPerUnit(finalCost);
                        inst.setSellPricePerUnit(standardCost);
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                        resultInstances.add(inst);
                    }
                } else {
                    InventoryInstance inst = new InventoryInstance();
                    inst.setInventoryItem(dbItem);
                    inst.setEntryDate(now);
                    inst.setQuantity(remainingQty);
                    inst.setCostPerUnit(finalCost);
                    inst.setSellPricePerUnit(standardCost);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    resultInstances.add(inst);
                }
                List<InventoryInstance> newInstances = resultInstances.stream().filter(i -> i.getId() == null).toList();
                if (!newInstances.isEmpty()) inventoryInstanceRepository.saveAll(newInstances);
            }

            InventoryProcurementOrder procurementOrder = new InventoryProcurementOrder();
            procurementOrder.setInventoryItem(dbItem);
            procurementOrder.setPendingInventoryList(resultInstances);
            procurementOrder.setProcurementDecision(request.getProcurementDecision());
            procurementOrder.setOrderId(request.getReferenceId());
            procurementOrder.setCreatedBy(request.getCreatedBy());
            procurementOrder.setInventoryProcurementStatus(InventoryProcurementStatus.COMPLETED);
            inventoryProcurementOrderRepository.save(procurementOrder);

            updateItemAvailability(dbItem.getInventoryItemId());
            return resultInstances;
        } catch (Exception e) {
            logger.error("Error in addInventory for item {}: {}", request.getInventoryItemId(), e.getMessage(), e);
            throw e;
        }
    }

    public Page<InventoryRequestGroupDTO> getGroupedRequests(
            int page, int size,
            String itemCode, String itemName,
            InventoryRequestSource source,
            InventoryApprovalStatus approvalStatus,
            Long referenceId
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<InventoryRequest> requestPage = inventoryRequestRepository.searchRequests(itemCode, itemName, source != null ? source.name() : null, approvalStatus != null ? approvalStatus.name() : null, referenceId, pageable);

            return requestPage.map(req -> {
                InventoryItem item = req.getInventoryItem();
                double totalQty;
                double requestQty;
                
                if (req.getRequestedQuantity() != null) {
                    totalQty = req.getRequestedQuantity().doubleValue();
                    requestQty = req.getApprovedQuantity() != null ? req.getApprovedQuantity().doubleValue() : 0.0;
                } else {
                    totalQty = req.getRequestedInstances().stream()
                            .map(InventoryInstance::getQuantity)
                            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                            .doubleValue();
                    requestQty = req.getRequestedInstances().stream()
                            .filter(inst -> inst.getInventoryInstanceStatus() == InventoryInstanceStatus.REQUESTED)
                            .map(InventoryInstance::getQuantity)
                            .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                            .doubleValue();
                }
                
                return new InventoryRequestGroupDTO(req.getId(), req.getSourceId(), item.getInventoryItemId(), item.getItemCode(), item.getName(), totalQty, requestQty, totalQty - requestQty, req.getRequestedDate(), req.getRequestSource(), req.getRequestedBy(), req.getApprovalStatus());
            });
        } catch (Exception e) {
            logger.error("Error in getGroupedRequests: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<InventoryInstance> getRequestedInstancesByReferenceAndItem(Long referenceId) {
        try {
            return inventoryRequestRepository.getReferenceById(referenceId).getRequestedInstances();
        } catch (Exception e) {
            logger.error("Error in getRequestedInstancesByReferenceAndItem for ref {}: {}", referenceId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    @Override
    public void updateProcurementStatus(Long procurementOrderId, InventoryProcurementStatus newStatus) {
        try {
            InventoryProcurementOrder order = inventoryProcurementOrderRepository.findById(procurementOrderId).orElseThrow(() -> new RuntimeException("Procurement order not found"));
            order.setInventoryProcurementStatus(newStatus);
            if (newStatus == InventoryProcurementStatus.COMPLETED) {
                boolean hasRequest = order.getInventoryRequest() != null;
                for (InventoryInstance instance : order.getPendingInventoryList()) {
                    instance.setInventoryInstanceStatus(hasRequest ? InventoryInstanceStatus.REQUESTED : InventoryInstanceStatus.AVAILABLE);
                }
            }
            inventoryProcurementOrderRepository.save(order);
        } catch (Exception e) {
            logger.error("Error in updateProcurementStatus for order {}: {}", procurementOrderId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    @Override
    public Page<InventoryProcurementOrderDTO> getProcurementOrders(
            int page, int size,
            InventoryProcurementStatus status,
            Long inventoryItemId,
            String createdBy
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<InventoryProcurementOrder> orderPage = inventoryProcurementOrderRepository.searchOrders(status != null ? status.name() : null, inventoryItemId, createdBy, null, pageable);
            return orderPage.map(order -> new InventoryProcurementOrderDTO(order.getId(), order.getInventoryItem() != null ? order.getInventoryItem().getItemCode() : null, order.getInventoryItem() != null ? order.getInventoryItem().getName() : null, order.getInventoryProcurementStatus(), order.getProcurementDecision(), order.getPendingInventoryList() != null ? order.getPendingInventoryList().size() : 0, order.getInventoryRequest() != null ? order.getInventoryRequest().getId() : null, order.getCreatedBy(), order.getCreationDate()));
        } catch (Exception e) {
            logger.error("Error in getProcurementOrders: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public List<InventoryInstance> addInventoryToExistingProcurement(AddInventoryRequest request, long procurementOrderId) {
        try {
            int inventoryItemId = request.getInventoryItemId();
            BigDecimal addedQty = BigDecimal.valueOf(request.getQuantity());
            BigDecimal costPerUnit = BigDecimal.valueOf(request.getCostPerUnit());
            InventoryProcurementOrder procurementOrder = inventoryProcurementOrderRepository.findById(procurementOrderId).orElseThrow(() -> new ResourceNotFoundException("Procurement order not found"));
            InventoryItem dbItem = inventoryItemRepository.findByActiveId(inventoryItemId);
            if (dbItem == null) throw new ResourceNotFoundException("Inventory item not found");

            List<InventoryInstance> resultInstances = new ArrayList<>();
            Date now = new Date();
            BigDecimal standardCost = Optional.ofNullable(dbItem.getProductFinanceSettings().getStandardCost()).map(BigDecimal::valueOf).orElse(BigDecimal.ZERO);
            BigDecimal finalCost = costPerUnit.compareTo(BigDecimal.ZERO) > 0 ? costPerUnit : standardCost;

            List<InventoryInstance> pendingInstances = inventoryInstanceRepository.inventoryInstanceByStatus(inventoryItemId, InventoryInstanceStatus.PENDING.name(), addedQty.doubleValue());
            BigDecimal remainingQty = addedQty;

            for (InventoryInstance inst : pendingInstances) {
                if (isUnitByNos(dbItem)) {
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    inst.setEntryDate(now);
                    inst.setCostPerUnit(finalCost);
                    inst.setSellPricePerUnit(standardCost);
                    resultInstances.add(inst);
                    remainingQty = remainingQty.subtract(BigDecimal.ONE);
                    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                } else {
                    BigDecimal canFill = inst.getQuantity().min(remainingQty);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    inst.setEntryDate(now);
                    inst.setCostPerUnit(finalCost);
                    inst.setSellPricePerUnit(standardCost);
                    resultInstances.add(inst);
                    remainingQty = remainingQty.subtract(canFill);
                    if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;
                }
            }
            inventoryInstanceRepository.saveAll(pendingInstances);

            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                if (isUnitByNos(dbItem)) {
                    int unitCount = remainingQty.intValue();
                    for (int i = 0; i < unitCount; i++) {
                        InventoryInstance inst = new InventoryInstance();
                        inst.setInventoryItem(dbItem);
                        inst.setEntryDate(now);
                        inst.setQuantity(BigDecimal.ONE);
                        inst.setCostPerUnit(finalCost);
                        inst.setSellPricePerUnit(standardCost);
                        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                        resultInstances.add(inst);
                    }
                } else {
                    InventoryInstance inst = new InventoryInstance();
                    inst.setInventoryItem(dbItem);
                    inst.setEntryDate(now);
                    inst.setQuantity(remainingQty);
                    inst.setCostPerUnit(finalCost);
                    inst.setSellPricePerUnit(standardCost);
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                    resultInstances.add(inst);
                }
                List<InventoryInstance> newInstances = resultInstances.stream().filter(i -> i.getId() == null).toList();
                if (!newInstances.isEmpty()) inventoryInstanceRepository.saveAll(newInstances);
            }

            if (procurementOrder.getPendingInventoryList() == null) procurementOrder.setPendingInventoryList(new ArrayList<>());
            procurementOrder.getPendingInventoryList().addAll(resultInstances);
            inventoryProcurementOrderRepository.save(procurementOrder);
            updateItemAvailability(dbItem.getInventoryItemId());
            return resultInstances;
        } catch (Exception e) {
            logger.error("Error in addInventoryToExistingProcurement for order {}: {}", procurementOrderId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    @Override
    public InventoryProcurementOrderDTO completeProcurementOrder(Long orderId, String completedBy) {
        try {
            InventoryProcurementOrder order = inventoryProcurementOrderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Procurement order not found"));
            if (order.getInventoryProcurementStatus() == InventoryProcurementStatus.COMPLETED) throw new IllegalStateException("Procurement order is already completed.");

            Date now = new Date();
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
                updateItemAvailability(pendingInstances.get(0).getInventoryItem().getInventoryItemId());
            }

            order.setInventoryProcurementStatus(InventoryProcurementStatus.COMPLETED);
            InventoryProcurementOrder savedOrder = inventoryProcurementOrderRepository.save(order);
            InventoryProcurementOrderDTO dto = new InventoryProcurementOrderDTO();
            dto.setId(savedOrder.getId());
            dto.setItemCode(savedOrder.getInventoryItem() != null ? savedOrder.getInventoryItem().getItemCode() : null);
            dto.setItemName(savedOrder.getInventoryItem() != null ? savedOrder.getInventoryItem().getName() : null);
            dto.setStatus(savedOrder.getInventoryProcurementStatus());
            dto.setDecision(savedOrder.getProcurementDecision());
            dto.setTotalInstances(savedOrder.getPendingInventoryList() != null ? savedOrder.getPendingInventoryList().size() : 0);
            dto.setRequestId(savedOrder.getInventoryRequest() != null ? savedOrder.getInventoryRequest().getId() : null);
            dto.setCreatedBy(savedOrder.getCreatedBy());
            return dto;
        } catch (Exception e) {
            logger.error("Error in completeProcurementOrder for order {}: {}", orderId, e.getMessage(), e);
            throw e;
        }
    }
}
