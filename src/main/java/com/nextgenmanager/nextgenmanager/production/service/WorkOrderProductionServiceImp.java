package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.production.DTO.WorkOrderProductionDTO;
import com.nextgenmanager.nextgenmanager.production.DTO.WorkOrderProductionRequestMapper;
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
    public WorkOrderProductionDTO createWorkOrderProduction(WorkOrderProduction newWorkOrderProduction) {
        if (newWorkOrderProduction.getSalesOrder() == null && newWorkOrderProduction.getWorkOrderProductionTemplate() == null) {
            logger.error("Not able to create work order due to missing SalesOrder or WorkOrderProductionTemplate.");
            throw new IllegalArgumentException("Sales Order or Work Order Template must not be null");
        }

        WorkOrderProductionTemplate workOrderProductionTemplate  = newWorkOrderProduction.getWorkOrderProductionTemplate();
        List<WorkOrderInventoryInstanceList> workOrderInventoryInstanceList = new ArrayList<>();
        List<WorkOrderInventoryInstanceList> pendingWorkOrderInventoryInstanceList = new ArrayList<>();

        int bomId =  workOrderProductionTemplate.getBom().getId();
        List<BomPosition> bomPositionList = bomService.getBom(bomId).getChildInventoryItems();
        workOrderProductionTemplate.setBom(bomService.getBom(workOrderProductionTemplate.getBom().getId()));
        newWorkOrderProduction.setWorkOrderProductionTemplate(workOrderProductionTemplate);
        for (BomPosition itemPosition : bomPositionList) {
            InventoryItem inventoryItem = itemPosition.getChildInventoryItem();
            logger.info("TESTTEST inventoryItemId: {}",inventoryItem.getInventoryItemId());
            WorkOrderInventoryInstanceList instanceListItem = new WorkOrderInventoryInstanceList();
            instanceListItem.setInventoryItem(inventoryItem);
            instanceListItem.setWorkOrderProduction(newWorkOrderProduction);
            logger.info("Processing inventory item ID: {} for quantity: {}", inventoryItem.getInventoryItemId(), itemPosition.getQuantity());
            double totalAvailableItems = itemPosition.getQuantity();
            if(newWorkOrderProduction.getQuantity()!=0){
                totalAvailableItems*=newWorkOrderProduction.getQuantity();
            }
            try {
                if (inventoryItem.getAvailableQuantity() >= totalAvailableItems) {
                    logger.info("Sufficient quantity available for inventory item ID: {}. Booking full quantity: {}",
                            inventoryItem.getInventoryItemId(),totalAvailableItems);

                    List<InventoryInstance> bookedItems = inventoryInstanceService.bookInventoryInstance(inventoryItem, totalAvailableItems);
                    instanceListItem.setInventoryInstanceList(bookedItems);
                    instanceListItem.setInventoryStatus(InventoryStatus.AVAILABLE);


                } else {
                    int availableQty = (int) inventoryItem.getAvailableQuantity();
                    logger.warn("Insufficient quantity for inventory item ID: {}. Available: {}, Required: {}",
                            inventoryItem.getInventoryItemId(), availableQty,totalAvailableItems);
                    double remainingQty = totalAvailableItems - availableQty;
                    logger.info("Requesting remaining quantity: {} for inventory item ID: {}", remainingQty, inventoryItem.getInventoryItemId());
                    List<InventoryInstance> inventoryInstanceList = new ArrayList<>();

                    // Only add booked items to this list — not affecting pending list
                    if (availableQty > 0) {
                        List<InventoryInstance> bookedItems = inventoryInstanceService.bookInventoryInstance(inventoryItem, availableQty);
                        instanceListItem.setInventoryStatus(InventoryStatus.AVAILABLE);
                        instanceListItem.setWorkOrderProduction(newWorkOrderProduction);
                        inventoryInstanceList.addAll(bookedItems);

                    }

                    // Handle only requested items for pending
                    if (remainingQty > 0) {
                        List<InventoryInstance> requestedList = inventoryInstanceService.requestInstance(inventoryItem, remainingQty);

                        if (!requestedList.isEmpty()) {
                            WorkOrderInventoryInstanceList instanceListItemList = new WorkOrderInventoryInstanceList();
                            instanceListItem.setInventoryInstanceList(requestedList);
                            instanceListItem.setInventoryStatus(InventoryStatus.PENDING);
                            instanceListItem.setWorkOrderProduction(newWorkOrderProduction);
                            instanceListItem.setInventoryItem(inventoryItem);
                            pendingWorkOrderInventoryInstanceList.add(instanceListItem);
                        }
                    }


                }
                workOrderInventoryInstanceList.add(instanceListItem);

            } catch (Exception e) {
                logger.error("Error booking/requesting inventory item ID: {} - {}", inventoryItem.getInventoryItemId(), e.getMessage(), e);
                throw e;
            }
        }

        newWorkOrderProduction.setWorkOrderInventoryInstanceLists(workOrderInventoryInstanceList);
        if (pendingWorkOrderInventoryInstanceList.isEmpty()){
            newWorkOrderProduction.setWorkOrderStatus(WorkOrderStatus.READY);
        }
        WorkOrderProduction savedWorkOrder = workOrderProductionRepository.save(newWorkOrderProduction);

        logger.info("Successfully created work order production job with ID: {}", savedWorkOrder.getId());

        if (!pendingWorkOrderInventoryInstanceList.isEmpty() && savedWorkOrder.isCreateChildItems()) {
            createChildWorkOrder(pendingWorkOrderInventoryInstanceList, savedWorkOrder, new HashSet<>(), 0);
        }

        return convertToDTO(savedWorkOrder);
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
            childWorkOrder.setWorkOrderStatus(WorkOrderStatus.DRAFT);
            childWorkOrder.setSalesOrder(parentWorkOrder.getSalesOrder());
            childWorkOrder.setParentWorkOrderProduction(parentWorkOrder);
            childWorkOrder.setWorkOrderProductionTemplate(template);
            logger.info("Creating child work order for inventory item ID: {} with BOM ID: {}", inventoryItemId, selectedBom.getId());

            // Save child work order
            WorkOrderProductionDTO savedChild = createWorkOrderProduction(childWorkOrder);


        }
    }



    private WorkOrderProductionDTO convertToDTO(WorkOrderProduction workOrderProduction){
//      TODO: Test this method also check how to handle bom if bom is not available
        List<BomPosition> bomPositionList = workOrderProduction.getWorkOrderProductionTemplate().getBom().getChildInventoryItems();
        WorkOrderProductionDTO dto = new WorkOrderProductionDTO();
        dto.setId(workOrderProduction.getId());
        dto.setSalesOrderNumber(workOrderProduction.getSalesOrder().getSalesOrderNumber());
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
        workOrderProduction.setQuantity(workOrderProduction.getQuantity());
        WorkOrderProductionTemplate workOrderProductionTemplate = bomService.getBomWOTemplateByBomId(workOrderProductionRequestMapper.getBom().getId());
        if(workOrderProductionTemplate==null){
            throw new RuntimeException("Work Order Details not available for BOM");
        }
        workOrderProduction.setWorkOrderProductionTemplate(workOrderProductionTemplate);
        workOrderProduction.setDueDate(workOrderProductionRequestMapper.getDueDate());
        workOrderProduction.setCreateChildItems(workOrderProductionRequestMapper.isCreateChildItems());
        return workOrderProduction;
    }

}
