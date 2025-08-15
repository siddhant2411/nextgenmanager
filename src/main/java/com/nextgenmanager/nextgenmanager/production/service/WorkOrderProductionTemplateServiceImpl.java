package com.nextgenmanager.nextgenmanager.production.service;



import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderJobList;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import com.nextgenmanager.nextgenmanager.production.repository.ProductionJobRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderProductionTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WorkOrderProductionTemplateServiceImpl implements WorkOrderProductionTemplateService{

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderProductionTemplateServiceImpl.class);

    @Autowired
    private WorkOrderProductionTemplateRepository workOrderProductionTemplateRepository;

    @Autowired
    private InventoryInstanceRepository inventoryInstanceRepository;

    @Autowired
    private ProductionJobService productionJobService;

    @Autowired
    private BomService bomService;

    @Autowired
    private InventoryItemService inventoryItemService;

    @Autowired
    private InventoryInstanceService inventoryInstanceService;

    public WorkOrderProductionTemplate getWorkOrderProductionTemplate(int id){

        logger.debug("Fetching workOrderProductionTemplate for ID: {}", id);
        return workOrderProductionTemplateRepository.findById(id)
                .filter(WOPTemplate -> WOPTemplate.getDeletedDate()==null)
                .orElseThrow(() -> {
                    logger.error("workOrderProductionTemplate not found for ID: {}", id);
                    return new RuntimeException("workOrderProductionTemplate not found for ID: " + id);
                });
    }

    @Override
    public WorkOrderProductionTemplate getWorkOrderProductionTemplateByBomId(int bomId) {
        return workOrderProductionTemplateRepository.findByBomId(bomId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found for BOM ID " + bomId));
    }

    ;

    public List<WorkOrderProductionTemplate> getWorkOrderProductionTemplateList(){

        logger.debug("Fetching all WOPTemplate");
        List<WorkOrderProductionTemplate> workOrderProductionTemplateList = workOrderProductionTemplateRepository.findAll().stream()
                .filter(WOPTemplate -> WOPTemplate.getDeletedDate() == null)
                .collect(Collectors.toList());
        logger.debug("Retrieved {} active workOrderProductionTemplate records", workOrderProductionTemplateList.size());
        return workOrderProductionTemplateList;
    };

    public WorkOrderProductionTemplate createWorkOrderProductionTemplate(WorkOrderProductionTemplate workOrderProductionTemplate) {
        logger.debug("Creating new workOrderProductionTemplate: {}", workOrderProductionTemplate);

        // 1. Calculate total labour cost
        List<WorkOrderJobList> workOrderJobLists = workOrderProductionTemplate.getWorkOrderJobLists();
        BigDecimal totalLabourCost = BigDecimal.ZERO;
        BigDecimal totalLabourHour =  BigDecimal.ZERO;

        // safe removal
        workOrderJobLists.removeIf(workOrderJobList -> workOrderJobList.getProductionJob() == null);
        for (WorkOrderJobList workOrderJobList : workOrderJobLists) {
            if(workOrderJobList.getProductionJob()!=null) {
                int actualWorkOrderJobList = workOrderJobList.getProductionJob().getId();
                if (actualWorkOrderJobList > 0) {
                    totalLabourCost = totalLabourCost.add(
                            productionJobService.getProductionJobById(actualWorkOrderJobList).getCostPerHour()
                                    .multiply(workOrderJobList.getNumberOfHours())
                    );
                    totalLabourHour = totalLabourHour.add(workOrderJobList.getNumberOfHours());
                }
            }
        }
        workOrderProductionTemplate.setEstimatedCostOfLabour(totalLabourCost);
        workOrderProductionTemplate.setEstimatedHours(totalLabourHour);
        int bomId = workOrderProductionTemplate.getBom().getId();
        Bom existingBom = bomService.getBom(bomId);  // Returns a managed entity
        workOrderProductionTemplate.setBom(existingBom);
        // 2. Calculate total BOM cost
        List<BomPosition> bomPositionList = bomService.getBom(bomId).getChildInventoryItems();
        BigDecimal totalBomCost = BigDecimal.ZERO;
        for (BomPosition bomPosition : bomPositionList) {
             int inventoryItemId  = bomPosition.getChildInventoryItem().getInventoryItemId();

            BigDecimal itemCost =BigDecimal.valueOf(inventoryItemService.getInventoryItem(inventoryItemId).getStandardCost());
            totalBomCost.add(itemCost);


        }
        workOrderProductionTemplate.setEstimatedCostOfBom(totalBomCost);

        // 3. Calculate overhead cost and total cost
        BigDecimal totalCostBeforeOverhead = totalLabourCost.add(totalBomCost);
        BigDecimal overheadCostPercentage = Optional.ofNullable(workOrderProductionTemplate.getOverheadCostPercentage())
                .orElse(BigDecimal.ZERO);

        BigDecimal overheadCost = totalCostBeforeOverhead
                .multiply(overheadCostPercentage.multiply(BigDecimal.valueOf(0.01)));
        BigDecimal totalCost = totalCostBeforeOverhead.add(overheadCost);

        workOrderProductionTemplate.setOverheadCostValue(overheadCost);
        workOrderProductionTemplate.setTotalCostOfWorkOrder(totalCost);

        // 4. Save and return
        WorkOrderProductionTemplate savedWorkOrderProductionTemplate = workOrderProductionTemplateRepository.save(workOrderProductionTemplate);
        logger.info("Successfully created workOrderProductionTemplate with ID: {}", savedWorkOrderProductionTemplate.getId());

        return savedWorkOrderProductionTemplate;
    }

    public WorkOrderProductionTemplate updateWorkOrderProductionTemplate(int id, WorkOrderProductionTemplate workOrderProductionTemplate) {
        logger.info("Attempting to update workOrderProductionTemplate with ID: {}", id);

        // Fetch existing entity to ensure it exists
        WorkOrderProductionTemplate existing = getWorkOrderProductionTemplate(id);

        // 1. Calculate total labour cost
        List<WorkOrderJobList> workOrderJobLists = workOrderProductionTemplate.getWorkOrderJobLists();
        BigDecimal totalLabourCost = BigDecimal.ZERO;
        BigDecimal totalLabourHour = BigDecimal.ZERO;
        for (WorkOrderJobList workOrderJobList : workOrderJobLists) {
            int actualWorkOrderJobList = workOrderJobList.getProductionJob().getId();
            totalLabourCost = totalLabourCost.add(
                    productionJobService.getProductionJobById(actualWorkOrderJobList).getCostPerHour()
                            .multiply(workOrderJobList.getNumberOfHours())
            );
            totalLabourHour = totalLabourHour.add(workOrderJobList.getNumberOfHours());
        }
        workOrderProductionTemplate.setEstimatedCostOfLabour(totalLabourCost);
        workOrderProductionTemplate.setEstimatedHours(totalLabourHour);

        // 2. Calculate total BOM cost
        int bomId = workOrderProductionTemplate.getBom().getId();
        List<BomPosition> bomPositionList = bomService.getBom(bomId).getChildInventoryItems();
        BigDecimal totalBomCost = BigDecimal.ZERO;
        for (BomPosition bomPosition : bomPositionList) {
            int inventoryItemId = bomPosition.getChildInventoryItem().getInventoryItemId();
            double standardCost = inventoryItemService.getInventoryItem(inventoryItemId).getStandardCost();
            BigDecimal itemCost = BigDecimal.valueOf(standardCost);

            BigDecimal quantity = BigDecimal.valueOf(bomPosition.getQuantity());  // quantity from BOM
            totalBomCost = totalBomCost.add(itemCost.multiply(quantity));  // sum (unitCost * quantity)
        }
        workOrderProductionTemplate.setEstimatedCostOfBom(totalBomCost);



        // 3. Calculate overhead cost and total cost
        BigDecimal totalCostBeforeOverhead = totalLabourCost.add(totalBomCost);
        BigDecimal overheadCost = totalCostBeforeOverhead
                .multiply(workOrderProductionTemplate.getOverheadCostPercentage().multiply(BigDecimal.valueOf(0.01)));
        BigDecimal totalCost = totalCostBeforeOverhead.add(overheadCost);

        workOrderProductionTemplate.setOverheadCostValue(overheadCost);
        workOrderProductionTemplate.setTotalCostOfWorkOrder(totalCost);

        // 4. Set ID explicitly to ensure update (optional but safe)
        workOrderProductionTemplate.setId(id);

        // 5. Save and return
        workOrderProductionTemplate.setWorkOrderProductionTemplateDocuments(getWorkOrderProductionTemplate(workOrderProductionTemplate.getId()).getWorkOrderProductionTemplateDocuments());
        WorkOrderProductionTemplate updatedWorkOrderProductionTemplate = workOrderProductionTemplateRepository.save(workOrderProductionTemplate);

        logger.info("Successfully updated workOrderProductionTemplate with ID: {}", updatedWorkOrderProductionTemplate.getId());

        return updatedWorkOrderProductionTemplate;
    }


    public void deleteWorkOrderProductionTemplate(int id){

        logger.debug("Attempting to soft workOrderProductionTemplate with ID: {}", id);
        WorkOrderProductionTemplate workOrderProductionTemplate = getWorkOrderProductionTemplate(id);
        workOrderProductionTemplate.setBom(null);
        workOrderProductionTemplate.setDeletedDate(new Date());
        workOrderProductionTemplateRepository.save(workOrderProductionTemplate);
        logger.info("Successfully soft deleted workOrderProductionTemplate with ID: {}", id);
    }
}
