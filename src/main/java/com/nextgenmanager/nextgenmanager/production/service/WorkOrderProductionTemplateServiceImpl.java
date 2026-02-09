package com.nextgenmanager.nextgenmanager.production.service;



import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderProductionTemplateResponseDTO;
import com.nextgenmanager.nextgenmanager.production.helper.OperationTotals;
import com.nextgenmanager.nextgenmanager.production.helper.OverheadTotals;
import com.nextgenmanager.nextgenmanager.production.enums.RoutingStatus;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderProductionTemplateResponseMapper;
import com.nextgenmanager.nextgenmanager.production.model.*;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderJobListRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderProductionTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
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

    private final InventoryItemService inventoryItemService;

    @Autowired
    private WorkOrderProductionTemplateResponseMapper woptResponseMapper;

    @Autowired
    private WorkOrderJobListRepository workOrderJobListRepository;

    @Autowired
    private WorkCenterService workCenterService;

    @Autowired
    private InventoryInstanceService inventoryInstanceService;

    public WorkOrderProductionTemplateServiceImpl(InventoryItemService inventoryItemService) {
        this.inventoryItemService = inventoryItemService;
    }

    private static final Map<RoutingStatus, RoutingStatus> NEXT_STATUS = Map.of(
            RoutingStatus.DRAFT, RoutingStatus.APPROVED,
            RoutingStatus.APPROVED, RoutingStatus.ACTIVE,
            RoutingStatus.ACTIVE, RoutingStatus.OBSOLETE
    );


    @Override
    public WorkOrderProductionTemplateResponseDTO getWorkOrderProductionTemplate(int id){

        logger.debug("Fetching workOrderProductionTemplate for ID: {}", id);
        WorkOrderProductionTemplate wopt = workOrderProductionTemplateRepository.findById(id)
                .filter(WOPTemplate -> WOPTemplate.getDeletedDate()==null)
                .orElseThrow(() -> {
                    logger.error("workOrderProductionTemplate not found for ID: {}", id);
                    return new ResourceNotFoundException("workOrderProductionTemplate not found for ID: " + id);
                });

        return woptResponseMapper.toDTO(wopt);
    }

    @Override
    public WorkOrderProductionTemplate getWorkOrderProductionTemplateEntity(int id){

        logger.debug("Fetching workOrderProductionTemplate for ID: {}", id);

        return workOrderProductionTemplateRepository.findById(id)
                .filter(WOPTemplate -> WOPTemplate.getDeletedDate()==null)
                .orElseThrow(() -> {
                    logger.error("workOrderProductionTemplate not found for ID: {}", id);
                    return new ResourceNotFoundException("workOrderProductionTemplate not found for ID: " + id);
                });
    }

    @Override
    public WorkOrderProductionTemplate getWorkOrderProductionTemplateByBomId(int bomId) {
        return workOrderProductionTemplateRepository.findByBomId(bomId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found for BOM ID " + bomId));
    }


    @Override
    public List<WorkOrderProductionTemplateResponseDTO> getWorkOrderProductionTemplateList(){

        logger.debug("Fetching all WOPTemplate");
        List<WorkOrderProductionTemplate> workOrderProductionTemplateList = workOrderProductionTemplateRepository.findAll().stream()
                .filter(WOPTemplate -> WOPTemplate.getDeletedDate() == null)
                .collect(Collectors.toList());
        logger.debug("Retrieved {} active workOrderProductionTemplate records", workOrderProductionTemplateList.size());

        return workOrderProductionTemplateList.stream().map(workOrderProductionTemplate -> woptResponseMapper.toDTO(workOrderProductionTemplate)).toList();
    };

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public WorkOrderProductionTemplateResponseDTO createWorkOrderProductionTemplate(WorkOrderProductionTemplate wopt) {

        try {


        validateWopt(wopt);

        List<WorkOrderJobList> jobLists = loadManagedEntities(wopt);

        OperationTotals op = calculateOperationTotals(jobLists);
        wopt.setTotalSetupTime(op.getTotalSetup());
        wopt.setTotalRunTime(op.getTotalRun());
        wopt.setEstimatedHours(op.getTotalHours());
        wopt.setEstimatedCostOfLabour(op.getTotalLabour());

        BigDecimal bomCost = calculateBomCost(wopt.getBom().getId());
        wopt.setEstimatedCostOfBom(bomCost);

        OverheadTotals overhead = calculateOverhead(
                op.getTotalLabour(), bomCost,
                Optional.ofNullable(wopt.getOverheadCostPercentage()).orElse(BigDecimal.ZERO)
        );

        wopt.setOverheadCostValue(overhead.getOverhead());
        wopt.setTotalCostOfWorkOrder(overhead.getTotalCost());



        WorkOrderProductionTemplate saved = workOrderProductionTemplateRepository.save(wopt);

        logger.info("Created WOPT id={} for BOM id={}, labourCost={}, bomCost={}, totalCost={}",
                saved.getId(), wopt.getBom().getId(), saved.getEstimatedCostOfLabour(),
                saved.getEstimatedCostOfBom(), saved.getTotalCostOfWorkOrder());

        WorkOrderProductionTemplateResponseDTO woptResponseDTO = woptResponseMapper.toDTO(saved);
            return woptResponseDTO;
        }
        catch (IllegalArgumentException e){
            logger.error(e.getMessage());
            throw new IllegalArgumentException(e.getMessage());
        }
        catch (ResourceNotFoundException e){
            logger.error(e.getMessage());
            throw new ResourceNotFoundException(e.getMessage());
        }
        catch (Exception e){
            logger.error("Error saving WOPT"+e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

    }

    private void validateWopt(WorkOrderProductionTemplate wopt) {
        if (wopt == null)
            throw new IllegalArgumentException("WOPT must not be null");

        if (wopt.getBom() == null || wopt.getBom().getId() == 0)
            throw new IllegalArgumentException("WOPT requires a valid BOM");

    }


    private List<WorkOrderJobList> loadManagedEntities(WorkOrderProductionTemplate incoming) {

        // 1. Always load BOM as a managed entity
        Bom managedBom = bomService.getBom(incoming.getBom().getId());
        incoming.setBom(managedBom);

        List<WorkOrderJobList> incomingJobs = Optional.ofNullable(incoming.getWorkOrderJobLists())
                .orElse(new ArrayList<>());

        List<WorkOrderJobList> managedList = new ArrayList<>();

        for (WorkOrderJobList inc : incomingJobs) {

            WorkOrderJobList job;

            // 2. Load existing job if ID present
            if (inc.getId() != 0) {
                job = workOrderJobListRepository.findById(inc.getId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException("Job not found: " + inc.getId())
                        );
            } else {
                job = new WorkOrderJobList();
            }

            // 3. Copy simple fields
            job.setOperationNumber(inc.getOperationNumber());
            job.setSetupTime(inc.getSetupTime());
            job.setRunTimePerUnit(inc.getRunTimePerUnit());
            job.setLabourCost(inc.getLabourCost());
            job.setOverheadCost(inc.getOverheadCost());
            job.setOperationDescription(inc.getOperationDescription());
            job.setIsParallelOperation(inc.getIsParallelOperation());
            job.setToolingRequirements(inc.getToolingRequirements());
            job.setSkillLevelRequired(inc.getSkillLevelRequired());

            // 4. Attach managed ProductionJob
            ProductionJob pj = productionJobService.getProductionJobEntityById(
                    inc.getProductionJob().getId()
            );
            job.setProductionJob(pj);

            // 5. Attach managed WorkCenter
            if (inc.getWorkCenter() != null && inc.getWorkCenter().getId() > 0) {
                WorkCenter wc = workCenterService.getWorkCenterEntityById(
                        inc.getWorkCenter().getId()
                );
                job.setWorkCenter(wc);
            }

            // 6. Attach to parent WOPT
            job.setWorkOrderProductionTemplate(incoming);

            managedList.add(job);
        }

        return managedList;
    }



    private OperationTotals calculateOperationTotals(List<WorkOrderJobList> jobLists) {

        BigDecimal totalSetup = BigDecimal.ZERO;
        BigDecimal totalRun = BigDecimal.ZERO;
        BigDecimal totalLabour = BigDecimal.ZERO;
        BigDecimal totalHours = BigDecimal.ZERO;

        for (WorkOrderJobList job : jobLists) {
            ProductionJob pj = job.getProductionJob();

            BigDecimal setup = Optional.ofNullable(job.getSetupTime())
                    .orElse(Optional.ofNullable(pj.getDefaultSetupTime()).orElse(BigDecimal.ZERO));

            BigDecimal run = Optional.ofNullable(job.getRunTimePerUnit())
                    .orElse(Optional.ofNullable(pj.getDefaultRunTimePerUnit()).orElse(BigDecimal.ZERO));

            BigDecimal costPerHour = Optional.ofNullable(pj.getCostPerHour()).orElse(BigDecimal.ZERO);

            BigDecimal opHours = setup.add(run);

            totalSetup = totalSetup.add(setup);
            totalRun = totalRun.add(run);
            totalLabour = totalLabour.add(costPerHour.multiply(opHours));
            totalHours = totalHours.add(opHours);
        }

        return new OperationTotals(totalSetup, totalRun, totalLabour, totalHours);
    }

    @Override
    public BigDecimal calculateBomCost(int bomId) {

        List<BomPosition> positions = bomService.getBomPositions(bomId);
        BigDecimal total = BigDecimal.ZERO;

        for (BomPosition pos : positions) {

            BigDecimal qty = BigDecimal.valueOf(pos.getQuantity());
            BigDecimal unitCost = BigDecimal.ZERO;

            if (pos.getChildBom().getParentInventoryItem() != null) {
                InventoryItem item = inventoryItemService.getInventoryItem(
                        pos.getChildBom().getParentInventoryItem().getInventoryItemId()
                );

                unitCost = Optional.ofNullable(item.getProductFinanceSettings())
                        .map(p -> BigDecimal.valueOf(p.getStandardCost()))
                        .orElse(BigDecimal.ZERO);

            } else {
                unitCost = calculateBomCost(pos.getChildBom().getId()); // recursion
            }

            total = total.add(unitCost.multiply(qty));
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }


    private OverheadTotals calculateOverhead(BigDecimal labour, BigDecimal bom, BigDecimal pct) {

        BigDecimal base = labour.add(bom);
        BigDecimal overhead = base.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = base.add(overhead);

        return new OverheadTotals(overhead, total);
    }



    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public WorkOrderProductionTemplateResponseDTO updateWorkOrderProductionTemplate(
            int id, WorkOrderProductionTemplate incoming) {

        logger.debug("Updating WOPT id={}", id);

        try {
            // 1. Load existing WOPT (managed entity)
            WorkOrderProductionTemplate existing = getWorkOrderProductionTemplateEntity(id);

            // 2. Validate incoming request
            validateWopt(incoming);


            existing.setCostingMethod(incoming.getCostingMethod());
            existing.setDetails(incoming.getDetails());
            existing.setOverheadCostPercentage(incoming.getOverheadCostPercentage());

            // If BOM is changed, update
            if (incoming.getBom() != null && incoming.getBom().getId() != existing.getBom().getId()) {
                existing.setBom(incoming.getBom());
            }

            // 4. Replace job list but attach to managed entities
            existing.getWorkOrderJobLists().clear();
            List<WorkOrderJobList> managedJobs = loadManagedEntities(incoming);
            existing.getWorkOrderJobLists().addAll(managedJobs);

            // 5. Recalculate operation totals
            OperationTotals op = calculateOperationTotals(managedJobs);
            existing.setTotalSetupTime(op.getTotalSetup());
            existing.setTotalRunTime(op.getTotalRun());
            existing.setEstimatedHours(op.getTotalHours());
            existing.setEstimatedCostOfLabour(op.getTotalLabour());

            // 6. Recalculate BOM cost
            BigDecimal bomCost = calculateBomCost(existing.getBom().getId());
            existing.setEstimatedCostOfBom(bomCost);

            // 7. Recalculate overhead
            OverheadTotals overhead = calculateOverhead(
                    op.getTotalLabour(),
                    bomCost,
                    Optional.ofNullable(incoming.getOverheadCostPercentage()).orElse(BigDecimal.ZERO)
            );

            existing.setOverheadCostValue(overhead.getOverhead());
            existing.setTotalCostOfWorkOrder(overhead.getTotalCost());

            // 8. Save
            WorkOrderProductionTemplate updated = workOrderProductionTemplateRepository.save(existing);

            logger.info("Updated WOPT id={} | labour={}, bom={}, total={}",
                    updated.getId(),
                    updated.getEstimatedCostOfLabour(),
                    updated.getEstimatedCostOfBom(),
                    updated.getTotalCostOfWorkOrder()
            );

            return woptResponseMapper.toDTO(updated);

        } catch (Exception e) {
            logger.error("Error updating WOPT id={} | {}", id, e.getMessage());
            throw e;
        }
    }


    @Override
    public void deleteWorkOrderProductionTemplate(int id){

        logger.debug("Attempting to soft workOrderProductionTemplate with ID: {}", id);
        WorkOrderProductionTemplate workOrderProductionTemplate  = workOrderProductionTemplateRepository.findById(id)
                .filter(WOPTemplate -> WOPTemplate.getDeletedDate()==null)
                .orElseThrow(() -> {
                    logger.error("workOrderProductionTemplate not found for ID: {}", id);
                    return new RuntimeException("workOrderProductionTemplate not found for ID: " + id);
                });
        workOrderProductionTemplate.setBom(null);
        workOrderProductionTemplate.setDeletedDate(new Date());
        workOrderProductionTemplateRepository.save(workOrderProductionTemplate);
        logger.info("Successfully soft deleted workOrderProductionTemplate with ID: {}", id);
    }

    @Override
    public WorkOrderProductionTemplate getActiveVersion(int bomId) {
        return null;
    }

    @Override
    public WorkOrderProductionTemplate createNewVersion(int bomId, WorkOrderProductionTemplate wopt) {
        return null;
    }

    @Override
    public void activateVersion(int versionId) {

    }

    @Override
    public WorkOrderJobList addJobToTemplate(int woptId, WorkOrderJobList job) {
        return null;
    }

    @Override
    public WorkOrderJobList updateJobInTemplate(int woptId, int jobId, WorkOrderJobList job) {
        return null;
    }

    @Override
    public void removeJobFromTemplate(int woptId, int jobId) {

    }

    @Override
    public List<WorkOrderJobList> getJobListForTemplate(int woptId) {
        return List.of();
    }

    @Override
    public WorkOrderProductionTemplate recalculateTotals(int woptId) {
        return null;
    }

    @Override
    public BigDecimal calculateSetupTime(int woptId) {
        return null;
    }

    @Override
    public BigDecimal calculateRunTime(int woptId) {
        return null;
    }

    @Override
    public BigDecimal calculateLabourCost(int woptId) {
        return null;
    }



    @Override
    public BigDecimal calculateOverheadCost(int woptId) {
        return null;
    }

    @Override
    public boolean validateOperationSequence(int woptId) {
        return false;
    }

    @Override
    public boolean validateEffectiveDates(Date effectiveFrom, Date effectiveTo) {
        return false;
    }

    @Override
    public boolean validateConsistencyWithBOM(int bomId, int woptId) {
        return false;
    }
}
