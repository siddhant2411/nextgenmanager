package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.production.DTO.WorkOrderProductionDTO;
import com.nextgenmanager.nextgenmanager.production.model.*;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderProductionRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public WorkOrderProduction getWorkOrderProductionJobById(int id) {
        return null;
    }

    @Override
    public Page<WorkOrderProductionDTO> getWorkOrderProductionList(WorkOrderProductionDTO workOrderProductionDTOFilter) {
        return null;
    }



    @Override
    @Transactional
    public WorkOrderProductionDTO createWorkOrderProduction(WorkOrderProduction newWorkOrderProduction) {
        if (newWorkOrderProduction.getSalesOrder() == null || newWorkOrderProduction.getWorkOrderProductionTemplate() == null) {
            logger.error("Not able to create work order due to missing SalesOrder or WorkOrderProductionTemplate.");
            throw new IllegalArgumentException("Sales Order or Work Order Template must not be null");
        }

        WorkOrderProductionTemplate workOrderProductionTemplate =
                workOrderProductionTemplateService.getWorkOrderProductionTemplate(
                        newWorkOrderProduction.getWorkOrderProductionTemplate().getId());
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

            try {
                if (inventoryItem.getAvailableQuantity() >= itemPosition.getQuantity()) {
                    logger.info("Sufficient quantity available for inventory item ID: {}. Booking full quantity: {}",
                            inventoryItem.getInventoryItemId(), itemPosition.getQuantity());

                    List<InventoryInstance> bookedItems = inventoryInstanceService.bookInventoryInstance(inventoryItem, itemPosition.getQuantity());
                    instanceListItem.setInventoryInstanceList(bookedItems);
                    instanceListItem.setInventoryStatus(InventoryStatus.AVAILABLE);


                } else {
                    int availableQty = (int) inventoryItem.getAvailableQuantity();
                    logger.warn("Insufficient quantity for inventory item ID: {}. Available: {}, Required: {}",
                            inventoryItem.getInventoryItemId(), availableQty, itemPosition.getQuantity());
                    double remainingQty = itemPosition.getQuantity() - availableQty;
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
        WorkOrderProduction savedWorkOrder = workOrderProductionRepository.save(newWorkOrderProduction);

        logger.info("Successfully created work order production job with ID: {}", savedWorkOrder.getId());

        if (!pendingWorkOrderInventoryInstanceList.isEmpty()) {
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
            InventoryItem inventoryItem = pendingItem.getInventoryInstanceList().get(0).getInventoryItem();
            int inventoryItemId = inventoryItem.getInventoryItemId();

            if (visitedInventoryIds.contains(inventoryItemId)) {
                logger.warn("Already processed inventory item ID: {} - skipping to avoid cycles", inventoryItemId);
                continue;
            }
            visitedInventoryIds.add(inventoryItemId);

            List<Bom> inventoryBomList = bomService.getBomByParentInventoryItem(inventoryItemId);
            for (Bom bom : inventoryBomList) {
                WorkOrderProduction workOrderProduction = new WorkOrderProduction();

                workOrderProduction.setWorkOrderStatus(WorkOrderStatus.DRAFT);
                workOrderProduction.setSalesOrder(parentWorkOrder.getSalesOrder());
                workOrderProduction.setParentWorkOrderProduction(parentWorkOrder);

                WorkOrderProductionTemplate template = bomService.getBomWOTemplateByBomId(bom.getId());
                if (template != null) {
                    workOrderProduction.setWorkOrderProductionTemplate(template);
                    logger.info("Creating child work order for inventory item ID: {} with BOM ID: {}", inventoryItemId, bom.getId());

                    createWorkOrderProduction(workOrderProduction);
                } else {
                    logger.warn("No WorkOrderProductionTemplate found for BOM ID: {}", bom.getId());
                }
            }
        }
    }


    private WorkOrderProductionDTO convertToDTO(WorkOrderProduction workOrderProduction){
//      TODO: Test this method also check how to handle bom if bom is not available
        List<BomPosition> bomPositionList = workOrderProduction.getWorkOrderProductionTemplate().getBom().getChildInventoryItems();
        WorkOrderProductionDTO dto = new WorkOrderProductionDTO();
        dto.setId(workOrderProduction.getId());
        dto.setSalesOrderNumber(workOrderProduction.getSalesOrder().getSalesOrderNumber());
        dto.setStatus(workOrderProduction.getWorkOrderStatus());
        dto.setBomName(bomPositionList.isEmpty() ? null : bomPositionList.get(0).getChildInventoryItem().getName());
        dto.setActualCost(workOrderProduction.getActualTotalCostOfWorkOrder() != null
                ? workOrderProduction.getActualTotalCostOfWorkOrder()
                : BigDecimal.ZERO);
        dto.setDueDate(workOrderProduction.getDueDate());
        dto.setCreationDate(workOrderProduction.getCreationDate());
        return  dto;
    }


}
