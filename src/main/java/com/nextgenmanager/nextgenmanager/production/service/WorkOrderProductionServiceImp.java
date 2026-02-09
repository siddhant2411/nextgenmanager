package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderProductionDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderProductionRequestMapper;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.*;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderProductionRepository;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import jakarta.persistence.criteria.Predicate;

@Service
public class WorkOrderProductionServiceImp implements WorkOrderProductionService{

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderProductionServiceImp.class);



    @Autowired
    private WorkOrderProductionRepository workOrderProductionRepository;

    @Autowired
    private InventoryItemService  inventoryItemService;

    @Autowired
    private InventoryInstanceService inventoryInstanceService;

    @Autowired
    private BomService bomService;

    @Autowired
    private WorkOrderProductionTemplateService workOrderProductionTemplateService;

    @Override
    public Optional<WorkOrderProduction> getWorkOrderProductionJobById(int id) {
        return workOrderProductionRepository.findById(id);
    }

    @Override
    public Page<WorkOrderProductionDTO> getWorkOrderProductionList(WorkOrderProductionDTO filterDTO, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size);

        Specification<WorkOrderProduction> spec = (root, query, cb) -> {
            Join<WorkOrderProduction, WorkOrderProductionTemplate> templateJoin = root.join("workOrderProductionTemplate", JoinType.LEFT);
            Join<WorkOrderProductionTemplate, Bom> bomJoin = templateJoin.join("bom", JoinType.INNER);
            Join<WorkOrderProduction, SalesOrder> salesOrderJoin = root.join("salesOrder", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();

            if (filterDTO.getId() > 0) {
                predicates.add(cb.equal(root.get("id"), filterDTO.getId()));
            }
            if (filterDTO.getSalesOrderNumber() != null && !filterDTO.getSalesOrderNumber().isEmpty()) {
                predicates.add(cb.like(cb.lower(salesOrderJoin.get("salesOrderNumber")), "%" + filterDTO.getSalesOrderNumber().toLowerCase() + "%"));
            }
            if (filterDTO.getBomName() != null && !filterDTO.getBomName().isEmpty()) {
                predicates.add(cb.like(cb.lower(bomJoin.get("bomName")), "%" + filterDTO.getBomName().toLowerCase() + "%"));
            }
            if (filterDTO.getStatus() != null) {
                predicates.add(cb.equal(root.get("workOrderStatus"), filterDTO.getStatus()));
            }
            if (filterDTO.getDueDate() != null) {
                predicates.add(cb.equal(root.get("dueDate"), filterDTO.getDueDate()));
            }
            if (filterDTO.getCreationDate() != null) {
                predicates.add(cb.equal(root.get("creationDate"), filterDTO.getCreationDate()));
            }

            // ✨ Add sorting here
            Path<?> sortPath;
            switch (sortBy) {
                case "salesOrderNumber":
                    sortPath = salesOrderJoin.get("salesOrderNumber");
                    break;
                case "bomName":
                    sortPath = bomJoin.get("bomName");
                    break;
                case "status":
                    sortPath = root.get("workOrderStatus");
                    break;
                case "dueDate":
                    sortPath = root.get("dueDate");
                    break;
                case "creationDate":
                    sortPath = root.get("creationDate");
                    break;
                default:
                    sortPath = root.get("id");
            }

            query.orderBy(sortDir.equalsIgnoreCase("asc") ? cb.asc(sortPath) : cb.desc(sortPath));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<WorkOrderProduction> entityPage = workOrderProductionRepository.findAll(spec, pageable);

        return entityPage.map(this::convertToDTO);


    }



    @Override
    @Transactional
    public WorkOrderProduction createWorkOrderProduction(WorkOrderProduction newWorkOrderProduction) {
//        if (newWorkOrderProduction.getSalesOrder() == null && newWorkOrderProduction.getWorkOrderProductionTemplate() == null) {
//            logger.error("Cannot create work order: Missing SalesOrder or WorkOrderProductionTemplate.");
//            throw new IllegalArgumentException("Sales Order or Work Order Template must not be null");
//        }

        WorkOrderProductionTemplate template = newWorkOrderProduction.getWorkOrderProductionTemplate();

        String workOrderNumber = generateNextWorkOrderNumber();
        newWorkOrderProduction.setWorkOrderNumber(workOrderNumber);

        int bomId = template.getBom().getId();

        Bom bom = bomService.getBom(bomId);
        template.setBom(bom);
        newWorkOrderProduction.setWorkOrderProductionTemplate(template);

        newWorkOrderProduction.setEstimatedCostOfBom(template.getEstimatedCostOfBom());
        newWorkOrderProduction.setEstimatedCostOfLabour(template.getEstimatedCostOfLabour());
        newWorkOrderProduction.setTotalEstimatedCostOfWorkOrder(template.getTotalCostOfWorkOrder());
        newWorkOrderProduction.setOverheadCostPercentage(template.getOverheadCostPercentage());
        newWorkOrderProduction.setOverheadCostValue(template.getOverheadCostValue());

//        TODO:  Fix this with BOM
        List<BomPosition> bomPositions = new ArrayList<>();
        List<WorkOrderInventoryInstanceList> allInstanceLists = new ArrayList<>();
        List<WorkOrderInventoryInstanceList> pendingInstanceLists = new ArrayList<>();

//        for (BomPosition bomPos : bomPositions) {
//            InventoryItem inventoryItem = bomPos.getChildInventoryItem();
//            double totalRequiredQty = bomPos.getQuantity() * (newWorkOrderProduction.getQuantity() != 0 ? newWorkOrderProduction.getQuantity() : 1);
//
//            WorkOrderInventoryInstanceList instanceListEntry = new WorkOrderInventoryInstanceList();
//            instanceListEntry.setInventoryItem(inventoryItem);
//            instanceListEntry.setWorkOrderProduction(newWorkOrderProduction);
//
//            try {
//                double availableQty = inventoryItem.getProductInventorySettings().getAvailableQuantity();
//                List<InventoryInstance> bookedInstances = new ArrayList<>();
//
//                if (availableQty >= totalRequiredQty) {
//                    logger.info("Booking available quantity: {} for item ID: {}", totalRequiredQty, inventoryItem.getInventoryItemId());
//                    bookedInstances = inventoryInstanceService.bookInventoryInstance(inventoryItem, totalRequiredQty);
//                    instanceListEntry.setInventoryStatus(InventoryStatus.AVAILABLE);
//                    instanceListEntry.setInventoryInstanceList(bookedInstances);
//                } else {
//                    logger.warn("Only {} available for item ID: {}, required: {}", availableQty, inventoryItem.getInventoryItemId(), totalRequiredQty);
//
//                    // Book what is available
//                    if (availableQty > 0) {
//                        bookedInstances = inventoryInstanceService.bookInventoryInstance(inventoryItem, availableQty);
//                    }
//
//                    // Request remaining
//                    double remainingQty = totalRequiredQty - availableQty;
////                    List<InventoryInstance> requestedInstances = inventoryInstanceService.requestInstance(inventoryItem, remainingQty);
////
////                    bookedInstances.addAll(requestedInstances);
//                    instanceListEntry.setInventoryStatus(InventoryStatus.PENDING);
//                    pendingInstanceLists.add(instanceListEntry);
//                }
//
//                instanceListEntry.setInventoryInstanceList(bookedInstances);
//                allInstanceLists.add(instanceListEntry);
//            } catch (Exception e) {
//                logger.error("Error while processing item ID {}: {}", inventoryItem.getInventoryItemId(), e.getMessage(), e);
//                throw e;
//            }
//        }

        newWorkOrderProduction.setWorkOrderInventoryInstanceLists(allInstanceLists);
        if (pendingInstanceLists.isEmpty()) {
//            newWorkOrderProduction.setWorkOrderStatus(WorkOrderStatus.READY);
        }

        WorkOrderProduction savedWorkOrder = workOrderProductionRepository.save(newWorkOrderProduction);
        logger.info("Work order created with ID: {} and Number: {}", savedWorkOrder.getId(), savedWorkOrder.getWorkOrderNumber());

        if (!pendingInstanceLists.isEmpty() && savedWorkOrder.isCreateChildItems()) {
            createChildWorkOrder(pendingInstanceLists, savedWorkOrder, new HashSet<>(), 0);
        }

        return savedWorkOrder;
    }


    private static final int MAX_RECURSION_DEPTH = 5;

    private void createChildWorkOrder(List<WorkOrderInventoryInstanceList> pendingWorkOrderInventoryInstanceList,
                                      WorkOrderProduction parentWorkOrder,
                                      Set<Integer> visitedInventoryIds,
                                      int depth) {

        if (depth > MAX_RECURSION_DEPTH) {
            logger.warn("Max recursion depth reached while creating child work orders for parent ID: {}", parentWorkOrder.getId());
            return;
        }

        for (WorkOrderInventoryInstanceList pendingItem : pendingWorkOrderInventoryInstanceList) {
            InventoryItem inventoryItem = pendingItem.getInventoryItem();
            int inventoryItemId = inventoryItem.getInventoryItemId();

            if (visitedInventoryIds.contains(inventoryItemId)) {
                logger.warn("Already processed inventory item ID: {} - skipping to avoid cycles", inventoryItemId);
                continue;
            }
            visitedInventoryIds.add(inventoryItemId);

            // ✅ Get the selected/default BOM for this inventory item
            Bom selectedBom = bomService.getBomByParentInventoryItem(inventoryItemId).get(0); // implement this if needed

            if (selectedBom == null) {
                logger.warn("No BOM found for inventory item ID: {}", inventoryItemId);
                continue;
            }

            WorkOrderProductionTemplate template = bomService.getBomWOTemplateByBomId(selectedBom.getId());
            if (template == null) {
                logger.warn("No WorkOrderProductionTemplate found for BOM ID: {}", selectedBom.getId());
                continue;
            }

            WorkOrderProduction childWorkOrder = new WorkOrderProduction();
            childWorkOrder.setQuantity(pendingItem.getInventoryInstanceList().size());
            childWorkOrder.setDueDate(parentWorkOrder.getDueDate()); // optional
//            childWorkOrder.setWorkOrderStatus(WorkOrderStatus.DRAFT);
            childWorkOrder.setSalesOrder(parentWorkOrder.getSalesOrder());
            childWorkOrder.setParentWorkOrderProduction(parentWorkOrder);
            childWorkOrder.setWorkOrderProductionTemplate(template);
            logger.info("Creating child work order for inventory item ID: {} with BOM ID: {}", inventoryItemId, selectedBom.getId());

            // Save child work order
            WorkOrderProduction savedChild = createWorkOrderProduction(childWorkOrder);


        }
    }



    private WorkOrderProductionDTO convertToDTO(WorkOrderProduction workOrderProduction){
//      TODO:  Fix this with BOM
        List<BomPosition> bomPositionList = new ArrayList<>();
        WorkOrderProductionDTO dto = new WorkOrderProductionDTO();
        dto.setId(workOrderProduction.getId());
        dto.setSalesOrderNumber(
                workOrderProduction.getSalesOrder() != null ?
                        workOrderProduction.getSalesOrder().getOrderNumber() : null);
        dto.setStatus(workOrderProduction.getWorkOrderStatus());
        dto.setBomName(bomPositionList.isEmpty() ? null : workOrderProduction.getWorkOrderProductionTemplate().getBom().getBomName());
        dto.setActualCost(workOrderProduction.getActualTotalCostOfWorkOrder() != null
                ? workOrderProduction.getActualTotalCostOfWorkOrder()
                : BigDecimal.ZERO);
        dto.setDueDate(workOrderProduction.getDueDate());
        dto.setCreationDate(workOrderProduction.getCreationDate());
        return  dto;
    }



    @Override
    public WorkOrderProduction mapWorkOrderProductionRequest(WorkOrderProductionRequestMapper workOrderProductionRequestMapper){
        WorkOrderProduction workOrderProduction = new WorkOrderProduction();
        workOrderProduction.setWorkOrderStatus(workOrderProductionRequestMapper.getStatus());
        workOrderProduction.setSalesOrder(workOrderProductionRequestMapper.getSalesOrder());
        workOrderProduction.setParentWorkOrderProduction(workOrderProductionRequestMapper.getParentWorkOrder());
        workOrderProduction.setParentWorkOrderProduction(workOrderProduction.getParentWorkOrderProduction());
        workOrderProduction.setQuantity(workOrderProductionRequestMapper.getQuantity());
        WorkOrderProductionTemplate workOrderProductionTemplate = bomService.getBomWOTemplateByBomId(workOrderProductionRequestMapper.getBom().getId());
        if(workOrderProductionTemplate==null){
            throw new RuntimeException("Work Order Details not available for BOM");
        }
        workOrderProduction.setWorkOrderProductionTemplate(workOrderProductionTemplate);
        workOrderProduction.setDueDate(workOrderProductionRequestMapper.getDueDate());
        workOrderProduction.setCreateChildItems(workOrderProductionRequestMapper.isCreateChildItems());
        return workOrderProduction;
    }


    @Transactional
    @Override
    public WorkOrderProductionDTO updateWorkOrderStatus(int id, WorkOrderStatus newStatus) {
        WorkOrderProduction workOrder = workOrderProductionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("WorkOrder not found"));

        WorkOrderStatus currentStatus = workOrder.getWorkOrderStatus();

        // Prevent invalid transitions
        if (!isValidStatusTransition(currentStatus, newStatus)) {
            throw new IllegalStateException("Invalid status transition: " + currentStatus + " → " + newStatus);
        }

        // Trigger consumption
//        if (newStatus == WorkOrderStatus.IN_PROGRESS && currentStatus == WorkOrderStatus.DRAFT) {
//            consumeInventoryForWorkOrder(id);
//        }
//
//        // Trigger revert
//        if (newStatus == WorkOrderStatus.CANCELLED &&
//                (currentStatus == WorkOrderStatus.READY || currentStatus == WorkOrderStatus.IN_PROGRESS)) {
//            revertInventoryForWorkOrder(id);
//        }

        // (Optional) Log status change
        logger.info("WorkOrder ID: {} status updated from {} → {}", id, currentStatus, newStatus);

        workOrder.setWorkOrderStatus(newStatus);
        WorkOrderProduction updated = workOrderProductionRepository.save(workOrder);

        return convertToDTO(updated);
    }

    private boolean isValidStatusTransition(WorkOrderStatus current, WorkOrderStatus next) {
        return switch (current) {
//            case DRAFT     -> next == WorkOrderStatus.IN_PROGRESS || next == WorkOrderStatus.CANCELLED;
//            case READY     -> next == WorkOrderStatus.COMPLETED || next == WorkOrderStatus.CANCELLED;
//            case IN_PROGRESS -> next == WorkOrderStatus.READY || next == WorkOrderStatus.CANCELLED;
            default        -> false;
        };
    }


    @Override
    @Transactional
    public WorkOrderProductionDTO updateWorkOrderProduction(WorkOrderProduction updatedWorkOrder) {

        WorkOrderProduction existing = workOrderProductionRepository.findById(updatedWorkOrder.getId())
                .orElseThrow(() -> new RuntimeException("Work order not found"));

        if (existing.getWorkOrderStatus() == WorkOrderStatus.IN_PROGRESS || existing.getWorkOrderStatus() == WorkOrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot update a work order that is already in progress or completed.");
        }

        // Update fields (you can expand this based on what is allowed to be updated)
        existing.setQuantity(updatedWorkOrder.getQuantity());
        existing.setDueDate(updatedWorkOrder.getDueDate());
        existing.setStartDate(updatedWorkOrder.getStartDate());

        existing.setSalesOrder(updatedWorkOrder.getSalesOrder());
        existing.setWorkOrderProductionTemplate(updatedWorkOrder.getWorkOrderProductionTemplate());
        existing.setParentWorkOrderProduction(updatedWorkOrder.getParentWorkOrderProduction());
//        existing.setWorkOrderNumber(updatedWorkOrder.getWorkOrderNumber());

        existing.setActualWorkHours(updatedWorkOrder.getActualWorkHours());
        existing.setActualCostOfBom(updatedWorkOrder.getActualCostOfBom());
        existing.setActualCostOfLabour(updatedWorkOrder.getActualCostOfLabour());
        existing.setActualTotalCostOfWorkOrder(updatedWorkOrder.getActualTotalCostOfWorkOrder());
        existing.setOverheadCostPercentage(updatedWorkOrder.getOverheadCostPercentage());
        existing.setOverheadCostValue(updatedWorkOrder.getOverheadCostValue());
        // Don’t update status or inventory lists here unless explicitly required

        WorkOrderProduction saved = workOrderProductionRepository.save(existing);

        logger.info("Work Order {} is updated ",existing.getWorkOrderNumber());
        return convertToDTO(saved);
    }

    private String generateNextWorkOrderNumber() {
        long count = workOrderProductionRepository.count() + 1;
        String year = String.valueOf(LocalDate.now().getYear());
        return "WO-" + year + "-" + String.format("%04d", count);
    }


}
