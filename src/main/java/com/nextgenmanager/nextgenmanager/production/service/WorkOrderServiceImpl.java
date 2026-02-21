package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.common.spec.GenericSpecification;
import com.nextgenmanager.nextgenmanager.production.dto.*;
import com.nextgenmanager.nextgenmanager.production.enums.*;
import com.nextgenmanager.nextgenmanager.production.helper.WorkOrderHistory;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderHistoryMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderListMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderMapper;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class WorkOrderServiceImpl implements WorkOrderService{

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderMaterialRepository workOrderMaterialRepository;

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    @Autowired
    private WorkOrderAuditService auditService;

    @Autowired
    private RoutingService routingService;

    @Autowired
    private  BomService bomService;

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderService.class);

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private WorkOrderListMapper workOrderListMapper;



    @Override
    public WorkOrderDTO getWorkOrder(int id) {

        WorkOrder workOrder = workOrderRepository.getReferenceById(id);
        logger.debug("Fetched Work Order: {}", workOrder.getWorkOrderNumber());
        return workOrderMapper.toDTO(workOrder);
    }

    private static final Map<String, String> JOIN_FIELD_MAP = Map.of(
            "bomName", "bom.bomName",
            "salesOrderNumber", "salesOrder.orderNumber",
            "workCenter", "workCenter.centerName",
            "parentWorkOrderNumber", "parentWorkOrder.workOrderNumber",
            "status", "workOrderStatus"
    );


    @Transactional
    @Override
    public WorkOrderDTO addWorkOrder(WorkOrderRequestDTO dto) {

        // Validate mandatory inputs

        logger.debug("Creating Work Order for BOM: {}, Routing: {}, Planned Qty: {}",
                dto.getBom() != null ? dto.getBom().getBomName() : "null",
                dto.getRouting() != null ? dto.getRouting().getId() : "null",
                dto.getPlannedQuantity());

        if (dto.getBom() == null) {
            throw new IllegalArgumentException("BOM is required to create Work Order");
        }

        if (dto.getPlannedQuantity() == null || dto.getPlannedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Planned quantity must be greater than zero");
        }

        //  Create Work Order header
        WorkOrder workOrder = new WorkOrder();
        workOrder.setWorkOrderNumber(generateWorkOrderNumber());
        workOrder.setSalesOrder(dto.getSalesOrder());
        workOrder.setBom(dto.getBom());

        workOrder.setRouting(routingService.getRoutingEntityByBom(dto.getBom().getId()));

        workOrder.setWorkCenter(dto.getWorkCenter());

        workOrder.setPlannedQuantity(dto.getPlannedQuantity());
        workOrder.setCompletedQuantity(BigDecimal.ZERO);
        workOrder.setScrappedQuantity(BigDecimal.ZERO);

        workOrder.setSourceType(dto.getSourceType());
        workOrder.setRemarks(dto.getRemarks());

        workOrder.setDueDate(dto.getDueDate());
        workOrder.setPlannedStartDate(dto.getPlannedStartDate());
        workOrder.setPlannedEndDate(dto.getPlannedEndDate());

        workOrder.setWorkOrderStatus(WorkOrderStatus.CREATED);

        // Save header first (ID needed)
        workOrder = workOrderRepository.save(workOrder);

        //  Explode BOM → WorkOrderMaterial
        List<WorkOrderMaterial> materials = new ArrayList<>();

        Bom bom = dto.getBom();
        bom = bomService.getBom(bom.getId());
        for (BomPosition bomItem : bomService.getBomPositions(bom.getId())) {

            WorkOrderMaterial wom = new WorkOrderMaterial();
            wom.setWorkOrder(workOrder);
            wom.setComponent(bomItem.getChildBom().getParentInventoryItem());

            BigDecimal baseQty = BigDecimal.valueOf(bomItem.getQuantity());
            BigDecimal plannedQty = dto.getPlannedQuantity();

            BigDecimal scrapPercent = bomItem.getScrapPercentage() != null
                    ? bomItem.getScrapPercentage()
                    : BigDecimal.ZERO;

            // ---- NET REQUIRED (Actual Need) ----
            BigDecimal netRequired = baseQty
                    .multiply(plannedQty)
                    .setScale(5, RoundingMode.HALF_UP);

            // ---- SCRAP MULTIPLIER ----
            BigDecimal scrapMultiplier = BigDecimal.ONE.add(
                    scrapPercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
            );

            // ---- PLANNED REQUIRED (With Scrap Buffer) ----
            BigDecimal plannedRequired = netRequired
                    .multiply(scrapMultiplier)
                    .setScale(5, RoundingMode.HALF_UP);

            wom.setNetRequiredQuantity(netRequired);
            wom.setPlannedRequiredQuantity(plannedRequired);
            wom.setScrappedQuantity(scrapPercent);

            wom.setIssuedQuantity(BigDecimal.ZERO);
            wom.setScrappedQuantity(BigDecimal.ZERO);
            wom.setIssueStatus(MaterialIssueStatus.NOT_ISSUED);

            materials.add(wom);
        }

        workOrderMaterialRepository.saveAll(materials);

        // Explode Routing → WorkOrderOperation
        List<WorkOrderOperation> operations = new ArrayList<>();

        for (RoutingOperation routingOp : workOrder.getRouting().getOperations()) {

            WorkOrderOperation woo = new WorkOrderOperation();
            woo.setWorkOrder(workOrder);
            woo.setRoutingOperation(routingOp);
            woo.setSequence(routingOp.getSequenceNumber());
            woo.setOperationName(routingOp.getName());
            woo.setWorkCenter(routingOp.getWorkCenter());

            woo.setPlannedQuantity(dto.getPlannedQuantity());
            woo.setCompletedQuantity(BigDecimal.ZERO);
            woo.setScrappedQuantity(BigDecimal.ZERO);

            woo.setStatus(OperationStatus.PLANNED);

            operations.add(woo);
        }

        workOrderOperationRepository.saveAll(operations);

        // Attach children to header (optional but clean)
        workOrder.setMaterials(materials);
        workOrder.setOperations(operations);

        //  Return DTO
        auditService.record(
                workOrder,
                WorkOrderEventType.CREATED,
                null,
                null,
                null,
                "WorkOrder created"
        );


        logger.info("Created Work Order: {} with {} materials and {} operations",
                workOrder.getWorkOrderNumber(),
                materials.size(),
                operations.size());
        return workOrderMapper.toDTO(workOrder);
    }

    @Override
    public Page<WorkOrderListDTO> getAllWorkOrders(
           FilterRequest request
    ) {

        Sort.Direction direction = Sort.Direction.fromString(request.getSortDir()); // safer
        String sortBy = request.getSortBy();
        if (JOIN_FIELD_MAP.containsKey(sortBy)) {
            sortBy = JOIN_FIELD_MAP.get(sortBy);
        }
        Sort sort = Sort.by(direction, sortBy);


        List<FilterCriteria> filters = request.getFilters();
        FilterCriteria filterDeleteDateIsNull = new FilterCriteria("deletedDate", "=", null);
        filters.add(filterDeleteDateIsNull);
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Specification<WorkOrder> spec = GenericSpecification.buildSpecification(filters, JOIN_FIELD_MAP);


        Page<WorkOrder> workOrders = workOrderRepository.findAll(spec, pageable);

        logger.debug("Fetched {} Work Orders for page {}, size {}",
                workOrders.getNumberOfElements(), pageable.getPageNumber(), pageable.getPageSize());
        //  Map to DTO
        return workOrders.map(workOrderListMapper::toDTO);
    }


    private String generateWorkOrderNumber() {
        Long seq = workOrderRepository.getNextWorkOrderSequence();
        return "WO-" + seq;
    }

    private BigDecimal calculateWorkOrderCompletedQuantity(WorkOrder workOrder) {

        // ---- OPERATION BASED COMPLETION ----
        List<WorkOrderOperation> operations =
                workOrderOperationRepository.findByWorkOrder(workOrder);

        BigDecimal operationCompletedUnits = operations.stream()
                .filter(op -> op.getDeletedDate() == null)
                .map(op -> op.getCompletedQuantity() != null
                        ? op.getCompletedQuantity()
                        : BigDecimal.ZERO)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);


        // ---- MATERIAL BASED COMPLETION ----
        List<WorkOrderMaterial> materials =
                workOrderMaterialRepository.findByWorkOrder(workOrder);

        Optional<BigDecimal> materialCompletedUnits = materials.stream()
                .filter(mat -> mat.getDeletedDate() == null)
                .map(mat -> {

                    if (mat.getNetRequiredQuantity() == null ||
                            mat.getNetRequiredQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                        return null;
                    }

                    BigDecimal plannedQty = workOrder.getPlannedQuantity() != null
                            ? workOrder.getPlannedQuantity()
                            : BigDecimal.ZERO;

                    if (plannedQty.compareTo(BigDecimal.ZERO) <= 0) {
                        return BigDecimal.ZERO;
                    }

                    BigDecimal issued = mat.getIssuedQuantity() != null
                            ? mat.getIssuedQuantity()
                            : BigDecimal.ZERO;

                    BigDecimal scrapped = mat.getScrappedQuantity() != null
                            ? mat.getScrappedQuantity()
                            : BigDecimal.ZERO;

                    // ✅ GOOD CONSUMED
                    BigDecimal goodConsumed = issued.subtract(scrapped);

                    if (goodConsumed.compareTo(BigDecimal.ZERO) <= 0) {
                        return BigDecimal.ZERO;
                    }

                    // Completion ratio
                    BigDecimal completionRatio = goodConsumed
                            .divide(mat.getNetRequiredQuantity(), 10, RoundingMode.DOWN);

                    // Units completed
                    BigDecimal completedByMaterial = completionRatio
                            .multiply(plannedQty)
                            .setScale(0, RoundingMode.DOWN);

                    return completedByMaterial.max(BigDecimal.ZERO);
                })
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo);


        // ---- FINAL COMPLETION ----
        return materialCompletedUnits
                .map(operationCompletedUnits::min)
                .orElse(operationCompletedUnits);
    }


    private static LocalDate toLocalDate(Date date) {
        return date == null
                ? null
                : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @Override
    public WorkOrderSummaryDTO getWorkOrderSummary() {
        return getWorkOrderSummary(LocalDate.now());
    }

    WorkOrderSummaryDTO getWorkOrderSummary(LocalDate today) {
        Specification<WorkOrder> spec = (root, query, cb) -> cb.isNull(root.get("deletedDate"));
        List<WorkOrder> workOrders = workOrderRepository.findAll(spec);

        long overdue = 0;
        long dueSoon = 0;
        long ready = 0;
        long inProgress = 0;
        long completedToday = 0;
        long blocked = 0;

        LocalDate dueSoonEnd = today.plusDays(3);

        for (WorkOrder workOrder : workOrders) {
            WorkOrderStatus status = workOrder.getWorkOrderStatus();
            boolean activeStatus = status != WorkOrderStatus.COMPLETED
                    && status != WorkOrderStatus.CLOSED
                    && status != WorkOrderStatus.CANCELLED;

            LocalDate dueDate = toLocalDate(workOrder.getDueDate());
            if (dueDate != null && dueDate.isBefore(today) && activeStatus) {
                overdue++;
            }
            if (dueDate != null
                    && (dueDate.isEqual(today) || dueDate.isAfter(today))
                    && (dueDate.isEqual(dueSoonEnd) || dueDate.isBefore(dueSoonEnd))
                    && activeStatus) {
                dueSoon++;
            }

            if (status == WorkOrderStatus.IN_PROGRESS) {
                inProgress++;
            }
            if (status == WorkOrderStatus.HOLD) {
                blocked++;
            }

            LocalDate plannedEndDate = toLocalDate(workOrder.getPlannedEndDate());
            if (plannedEndDate != null && plannedEndDate.isEqual(today)) {
                completedToday++;
            }

            if (status == WorkOrderStatus.READY_FOR_INSPECTION
                   ) {
                ready++;
            }
        }

        return new com.nextgenmanager.nextgenmanager.production.dto.WorkOrderSummaryDTO(
                overdue,
                dueSoon,
                ready,
                inProgress,
                completedToday,
                blocked
        );
    }


    @Transactional
    @Override
    public WorkOrderDTO updateWorkOrder(int workOrderId, WorkOrderRequestDTO dto) {

        logger.debug("Updating WorkOrder id={}", workOrderId);

        // Fetch existing Work Order
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED) {
            logger.warn(
                    "Update rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "WorkOrder can only be updated in CREATED status"
            );
        }

        // Forbidden changes (explicit checks = future-proof)
        if (dto.getBom() != null &&
                !(dto.getBom().getId() == workOrder.getBom().getId())) {

            logger.error("Attempt to change BOM for WorkOrder {}", workOrder.getWorkOrderNumber());
            throw new IllegalStateException("BOM cannot be changed after WorkOrder creation");
        }

        if (dto.getRouting() != null &&
                !dto.getRouting().getId().equals(workOrder.getRouting().getId())) {

            logger.error("Attempt to change Routing for WorkOrder {}", workOrder.getWorkOrderNumber());
            throw new IllegalStateException("Routing cannot be changed after WorkOrder creation");
        }

        // Update allowed fields
        workOrder.setRemarks(dto.getRemarks());
        workOrder.setDueDate(dto.getDueDate());
        workOrder.setPlannedStartDate(dto.getPlannedStartDate());
        workOrder.setPlannedEndDate(dto.getPlannedEndDate());
        workOrder.setWorkCenter(dto.getWorkCenter());
        workOrder.setActualStartDate(dto.getActualStartDate());
        workOrder.setActualEndDate(dto.getActualEndDate());

        // Capture old quantity for audit trail
        BigDecimal oldQty = workOrder.getPlannedQuantity();
        BigDecimal newQty = oldQty;

        BigDecimal oldScrappedQty = workOrder.getScrappedQuantity();
        BigDecimal newScrappedQty = oldScrappedQty;
        if (dto.getPlannedQuantity() != null &&
                dto.getPlannedQuantity().compareTo(BigDecimal.ZERO) > 0) {

            // ERP Rule: Prevent quantity changes if operations/materials already in progress
            List<WorkOrderOperation> operations =
                    workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());

            if (!operations.isEmpty() && ! workOrder.getWorkOrderStatus().equals(WorkOrderStatus.CREATED)) {
                logger.error(
                        "Quantity change rejected: operations already exist for WorkOrder {}",
                        workOrder.getWorkOrderNumber()
                );
                throw new IllegalStateException(
                        "Planned quantity cannot be changed after operations are created"
                );
            }

            // Check if any materials have been issued
            List<WorkOrderMaterial> materials =
                    workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());

            boolean anyMaterialIssued = materials.stream()
                    .anyMatch(m -> m.getIssueStatus() != MaterialIssueStatus.NOT_ISSUED);

            if (anyMaterialIssued) {
                logger.error(
                        "Quantity change rejected: materials already issued for WorkOrder {}",
                        workOrder.getWorkOrderNumber()
                );
                throw new IllegalStateException(
                        "Planned quantity cannot be changed when materials have been issued"
                );
            }

            logger.debug(
                    "Updating planned quantity for WorkOrder {} from {} to {}",
                    workOrder.getWorkOrderNumber(),
                    oldQty,
                    dto.getPlannedQuantity()
            );

            // Calculate ratio
            BigDecimal quantityRatio = dto.getPlannedQuantity().divide(
                    oldQty,
                    10,
                    RoundingMode.HALF_UP
            );

            // Update all material quantities proportionally
            for (WorkOrderMaterial material : materials) {

                BigDecimal oldNetQty = material.getNetRequiredQuantity();
                BigDecimal oldPlannedQty = material.getPlannedRequiredQuantity();

                BigDecimal newNetQty = oldNetQty
                        .multiply(quantityRatio)
                        .setScale(5, RoundingMode.HALF_UP);

                BigDecimal newPlannedQty = oldPlannedQty
                        .multiply(quantityRatio)
                        .setScale(5, RoundingMode.HALF_UP);

                logger.debug(
                        "Recalculating material {} | Net: {} -> {} | Planned: {} -> {} | WO {}",
                        material.getComponent().getItemCode(),
                        oldNetQty,
                        newNetQty,
                        oldPlannedQty,
                        newPlannedQty,
                        workOrder.getWorkOrderNumber()
                );

                material.setNetRequiredQuantity(newNetQty);
                material.setPlannedRequiredQuantity(newPlannedQty);

            }

            workOrderMaterialRepository.saveAll(materials);

            // Update all operation quantities proportionally
            for (WorkOrderOperation operation : operations) {
                BigDecimal newOperationQty = operation.getPlannedQuantity()
                        .multiply(quantityRatio)
                        .setScale(5, java.math.RoundingMode.HALF_UP);

                logger.debug(
                        "Recalculating operation {} quantity from {} to {} for WorkOrder {}",
                        operation.getSequence(),
                        operation.getPlannedQuantity(),
                        newOperationQty,
                        workOrder.getWorkOrderNumber()
                );

                operation.setPlannedQuantity(newOperationQty);
            }

            workOrderOperationRepository.saveAll(operations);

            newQty = dto.getPlannedQuantity();
            workOrder.setPlannedQuantity(newQty);

            newScrappedQty = dto.getScrappedQuantity();
            workOrder.setScrappedQuantity(newScrappedQty);
        }

        if(workOrder.getWorkOrderStatus() == WorkOrderStatus.CREATED) {
            workOrder.setSourceType(dto.getSourceType());
            if (dto.getSourceType() != null) {

                if (dto.getSourceType() == WorkOrderSourceType.SALES_ORDER) {
                    if (dto.getSalesOrder() == null) {
                        logger.error(
                                "Source type change rejected: WorkOrder {} has no associated Sales Order",
                                workOrder.getWorkOrderNumber()
                        );
                        throw new IllegalStateException(
                                "Cannot set source type to SALES_ORDER when no Sales Order is linked"
                        );
                    }
                    workOrder.setSalesOrder(dto.getSalesOrder());
                    workOrder.setParentWorkOrder(null);
                } else if (dto.getSourceType() == WorkOrderSourceType.PARENT_WORK_ORDER) {
                    if (dto.getParentWorkOrder() == null) {
                        logger.error(
                                "Source type change rejected: WorkOrder {} has no associated Parent Work Order",
                                workOrder.getWorkOrderNumber()
                        );
                        throw new IllegalStateException(
                                "Cannot set source type to PARENT_WORK_ORDER when no Parent Work Order is linked"
                        );
                    }
                    workOrder.setParentWorkOrder(dto.getParentWorkOrder());
                    workOrder.setSalesOrder(null);
                } else {
                    workOrder.setSalesOrder(null);
                    workOrder.setParentWorkOrder(null);

                }
                logger.debug(
                        "Updating source type for WorkOrder {} from {} to {}",
                        workOrder.getWorkOrderNumber(),
                        workOrder.getSourceType(),
                        dto.getSourceType()
                );
            }
        }

        //  Persist
        WorkOrder updated = workOrderRepository.save(workOrder);

        // Record audit with correct old and new values

        if(!Objects.equals(oldQty, newQty)) {
            auditService.record(
                    workOrder,
                    WorkOrderEventType.UPDATED,
                    "plannedQuantity",
                    oldQty.toString(),
                    newQty.toString(),
                    "Planned quantity updated from " + oldQty + " to " + newQty
            );
        }
        if(!Objects.equals(oldScrappedQty, newScrappedQty)) {
            auditService.record(
                    workOrder,
                    WorkOrderEventType.UPDATED,
                    "scrappedQuantity",
                    oldScrappedQty.toString(),
                    newScrappedQty.toString(),
                    "Scrapped quantity updated from " + oldScrappedQty + " to " + newScrappedQty
            );
        }




        logger.info(
                "WorkOrder {} updated successfully",
                updated.getWorkOrderNumber()
        );

        // Return DTO
        return workOrderMapper.toDTO(updated);
    }

    @Transactional
    @Override
    public WorkOrderDTO releaseWorkOrder(int workOrderId) {

        logger.debug("Releasing WorkOrder id={}", workOrderId);

        //  Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById( workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED) {
            logger.warn(
                    "Release rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "Only WorkOrders in CREATED status can be released"
            );
        }

        //  Validate BOM & Routing
        if (workOrder.getBom() == null || workOrder.getRouting() == null) {
            logger.error(
                    "Release failed for WorkOrder {} due to missing BOM/Routing",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException("BOM and Routing are mandatory to release WorkOrder");
        }

        //  Validate materials
        List<WorkOrderMaterial> materials =
                workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());

        if (materials.isEmpty()) {
            logger.error(
                    "Release failed for WorkOrder {}: no materials found",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException("WorkOrder must have materials before release");
        }

        //  Validate operations
        List<WorkOrderOperation> operations =
                workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());

        if (operations.isEmpty()) {
            logger.error(
                    "Release failed for WorkOrder {}: no operations found",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException("WorkOrder must have operations before release");
        }

        //  Update Work Order status
        workOrder.setWorkOrderStatus(WorkOrderStatus.RELEASED);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} released successfully",
                workOrder.getWorkOrderNumber()
        );

        //  Set first operation READY
        WorkOrderOperation firstOperation = operations.get(0);
        firstOperation.setStatus(OperationStatus.READY);
        workOrderOperationRepository.save(firstOperation);

        logger.info(
                "First operation [{} - {}] set to READY for WorkOrder {}",
                firstOperation.getSequence(),
                firstOperation.getOperationName(),
                workOrder.getWorkOrderNumber()
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.RELEASED,
                "status",
                "CREATED",
                "RELEASED",
                "WorkOrder released"
        );

        //  Return updated DTO
        return workOrderMapper.toDTO(workOrder);
    }

    @Transactional
    @Override
    public void startOperation(Long operationId) {

        logger.info("Starting WorkOrderOperation id={}", operationId);

        // Fetch operation
        WorkOrderOperation operation =
                workOrderOperationRepository.findById(operationId)
                        .orElseThrow(() -> {
                            logger.error("Operation not found id={}", operationId);
                            return new EntityNotFoundException("Operation not found");
                        });

        WorkOrder workOrder = operation.getWorkOrder();

        //  Validate WorkOrder status
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.RELEASED &&
                workOrder.getWorkOrderStatus() != WorkOrderStatus.IN_PROGRESS) {

            logger.warn(
                    "Cannot start operation {} for WorkOrder {} due to WO status {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );

            throw new IllegalStateException(
                    "Operation can only be started when WorkOrder is RELEASED or IN_PROGRESS"
            );
        }

        // Validate operation status
        if (operation.getStatus() != OperationStatus.READY) {
            logger.warn(
                    "Cannot start operation {} for WorkOrder {} due to operation status {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    operation.getStatus()
            );
            throw new IllegalStateException("Only READY operations can be started");
        }

        //  Ensure no other operation is IN_PROGRESS
        boolean otherOpInProgress =
                workOrderOperationRepository
                        .existsByWorkOrderAndStatus(workOrder, OperationStatus.IN_PROGRESS);

        if (otherOpInProgress) {
            logger.warn(
                    "Another operation already IN_PROGRESS for WorkOrder {}",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException(
                    "Another operation is already in progress for this WorkOrder"
            );
        }

        //  Validate previous operation (sequence enforcement)
        WorkOrderOperation previousOperation =
                workOrderOperationRepository
                        .findTopByWorkOrderAndSequenceLessThanOrderBySequenceDesc(
                                workOrder, operation.getSequence()
                        );

        if (previousOperation != null &&
                previousOperation.getStatus() != OperationStatus.COMPLETED) {

            logger.warn(
                    "Cannot start operation {} before completing previous operation {} for WorkOrder {}",
                    operation.getSequence(),
                    previousOperation.getSequence(),
                    workOrder.getWorkOrderNumber()
            );

            throw new IllegalStateException(
                    "Previous operation must be COMPLETED before starting this operation"
            );
        }

        //  Start operation
        operation.setStatus(OperationStatus.IN_PROGRESS);
        operation.setActualStartDate(new Date());
        workOrderOperationRepository.save(operation);

        logger.info(
                "Operation [{} - {}] started for WorkOrder {}",
                operation.getSequence(),
                operation.getOperationName(),
                workOrder.getWorkOrderNumber()
        );

        // Update WorkOrder status if first operation
        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.RELEASED) {
            workOrder.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
            workOrder.setActualStartDate(new Date());
            workOrderRepository.save(workOrder);

            logger.info(
                    "WorkOrder {} moved to IN_PROGRESS",
                    workOrder.getWorkOrderNumber()
            );
        }

        auditService.record(
                workOrder,
                WorkOrderEventType.OPERATION_STARTED,
                "operation",
                null,
                operation.getSequence() + " - " + operation.getOperationName(),
                "Operation started"
        );
    }

    @Transactional
    @Override
    public void issueMaterials(IssueWorkOrderMaterialDTO issueDTO) {

        logger.info("Issuing materials for WorkOrder id={}", issueDTO.getWorkOrderId());

        // Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById(issueDTO.getWorkOrderId())
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", issueDTO.getWorkOrderId());
                    return new EntityNotFoundException("WorkOrder not found");
                });

        // Validate WorkOrder status
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.RELEASED &&
                workOrder.getWorkOrderStatus() != WorkOrderStatus.IN_PROGRESS) {

            throw new IllegalStateException(
                    "Materials can only be issued when WorkOrder is RELEASED or IN_PROGRESS"
            );
        }

        for (IssueWorkOrderMaterialDTO.MaterialIssueItem item : issueDTO.getMaterials()) {

            WorkOrderMaterial material = workOrderMaterialRepository
                    .findById(item.getWorkOrderMaterialId())
                    .orElseThrow(() -> new EntityNotFoundException("WorkOrderMaterial not found"));

            // Validate material belongs to work order
            if (!(material.getWorkOrder().getId() ==workOrder.getId())) {
                throw new IllegalStateException("Material does not belong to this WorkOrder");
            }

            // ----- SAFE VALUE EXTRACTION -----

            BigDecimal currentIssued = material.getIssuedQuantity() != null
                    ? material.getIssuedQuantity()
                    : BigDecimal.ZERO;

            BigDecimal currentScrapped = material.getScrappedQuantity() != null
                    ? material.getScrappedQuantity()
                    : BigDecimal.ZERO;

            BigDecimal newIssued = item.getIssuedQuantity() != null
                    ? item.getIssuedQuantity()
                    : BigDecimal.ZERO;

            BigDecimal newScrap = item.getScrappedQuantity() != null
                    ? item.getScrappedQuantity()
                    : BigDecimal.ZERO;

            if (newIssued.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Issued quantity must be greater than zero");
            }

            if (newScrap.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Scrap quantity cannot be negative");
            }

            // ----- CALCULATIONS -----

            BigDecimal totalIssued = currentIssued.add(newIssued);
            BigDecimal totalScrapped = currentScrapped.add(newScrap);

            // Prevent over-issue beyond planned quantity
            if (totalIssued.compareTo(material.getPlannedRequiredQuantity()) > 0) {
                throw new IllegalStateException(
                        "Issued quantity exceeds planned required quantity"
                );
            }

            // Scrap cannot exceed total issued
            if (totalScrapped.compareTo(totalIssued) > 0) {
                throw new IllegalStateException(
                        "Scrap quantity cannot exceed issued quantity"
                );
            }

            // Update quantities
            material.setIssuedQuantity(totalIssued);
            material.setScrappedQuantity(totalScrapped);

            // ----- STATUS BASED ON NET REQUIREMENT -----

            BigDecimal goodConsumed = totalIssued.subtract(totalScrapped);

            if (goodConsumed.compareTo(material.getNetRequiredQuantity()) >= 0) {
                material.setIssueStatus(MaterialIssueStatus.ISSUED);

                logger.info(
                        "Material {} fully satisfied for WorkOrder {}",
                        material.getComponent().getItemCode(),
                        workOrder.getWorkOrderNumber()
                );

            } else if (totalIssued.compareTo(BigDecimal.ZERO) > 0) {
                material.setIssueStatus(MaterialIssueStatus.PARTIAL_ISSUED);

                logger.info(
                        "Material {} partially issued (Good: {}/{}) for WorkOrder {}",
                        material.getComponent().getItemCode(),
                        goodConsumed,
                        material.getNetRequiredQuantity(),
                        workOrder.getWorkOrderNumber()
                );

            } else {
                material.setIssueStatus(MaterialIssueStatus.NOT_ISSUED);
            }

            workOrderMaterialRepository.save(material);

            // Audit
            auditService.record(
                    workOrder,
                    WorkOrderEventType.UPDATED,
                    "materialIssue",
                    material.getComponent().getItemCode(),
                    totalIssued.toString(),
                    "Issued: " + newIssued + ", Scrap: " + newScrap
            );
        }

        logger.info(
                "Materials issued successfully for WorkOrder {}",
                workOrder.getWorkOrderNumber()
        );

        BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);
        workOrder.setCompletedQuantity(totalCompleted);

        workOrderRepository.save(workOrder);
    }


    @Transactional
    @Override
    public void completeOperationPartial(PartialOperationCompleteDTO partialCompleteDTO) {

        logger.info("Completing operation id={} with partial qty={}",
                partialCompleteDTO.getOperationId(),
                partialCompleteDTO.getCompletedQuantity());

        // Fetch operation
        WorkOrderOperation operation = workOrderOperationRepository
                .findById(partialCompleteDTO.getOperationId())
                .orElseThrow(() -> {
                    logger.error("Operation not found id={}", partialCompleteDTO.getOperationId());
                    return new EntityNotFoundException("Operation not found");
                });

        WorkOrder workOrder = operation.getWorkOrder();

        // Validate operation status
        if (operation.getStatus() != OperationStatus.IN_PROGRESS &&
                operation.getStatus() != OperationStatus.READY) {

            logger.warn(
                    "Cannot complete operation {} for WorkOrder {} due to status {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    operation.getStatus()
            );
            throw new IllegalStateException(
                    "Only READY or IN_PROGRESS operations can be completed"
            );
        }

        // Validate quantity
        if (partialCompleteDTO.getCompletedQuantity() == null ||
                partialCompleteDTO.getCompletedQuantity().compareTo(BigDecimal.ZERO) <= 0) {

            logger.error("Invalid completed quantity {} for operation {}",
                    partialCompleteDTO.getCompletedQuantity(),
                    partialCompleteDTO.getOperationId());
            throw new IllegalArgumentException(
                    "Completed quantity must be greater than zero"
            );
        }

        // Calculate new completed quantity
        BigDecimal currentCompleted = operation.getCompletedQuantity();
        BigDecimal newCompleted = currentCompleted.add(partialCompleteDTO.getCompletedQuantity());

        // Validate against planned quantity (if over-completion not allowed)
        if (!Boolean.TRUE.equals(operation.getAllowOverCompletion()) &&
                newCompleted.compareTo(operation.getPlannedQuantity()) > 0) {

            logger.warn(
                    "Over-completion not allowed for operation {} (planned={}, completed={})",
                    operation.getSequence(),
                    operation.getPlannedQuantity(),
                    newCompleted
            );
            throw new IllegalStateException(
                    "Completed quantity exceeds planned quantity"
            );
        }

        // Add scrapped quantity if provided
        if (partialCompleteDTO.getScrappedQuantity() != null &&
                partialCompleteDTO.getScrappedQuantity().compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal newScrapped = operation.getScrappedQuantity().add(
                    partialCompleteDTO.getScrappedQuantity()
            );
            operation.setScrappedQuantity(newScrapped);
        }

        // Update operation
        operation.setCompletedQuantity(newCompleted);
        operation.setStatus(OperationStatus.IN_PROGRESS);

        if (operation.getActualStartDate() == null) {
            operation.setActualStartDate(new Date());
        }

        // Mark as COMPLETED if all planned quantity is done
        if (newCompleted.compareTo(operation.getPlannedQuantity()) >= 0) {
            operation.setStatus(OperationStatus.COMPLETED);
            operation.setActualEndDate(new Date());

            logger.info(
                    "Operation [{} - {}] fully COMPLETED for WorkOrder {}",
                    operation.getSequence(),
                    operation.getOperationName(),
                    workOrder.getWorkOrderNumber()
            );

            // Unlock next operation (if exists)
            WorkOrderOperation nextOperation =
                    workOrderOperationRepository
                            .findTopByWorkOrderAndSequenceGreaterThanOrderBySequenceAsc(
                                    workOrder, operation.getSequence()
                            );

            if (nextOperation != null) {
                nextOperation.setStatus(OperationStatus.READY);
                workOrderOperationRepository.save(nextOperation);

                logger.info(
                        "Next operation [{} - {}] set to READY for WorkOrder {}",
                        nextOperation.getSequence(),
                        nextOperation.getOperationName(),
                        workOrder.getWorkOrderNumber()
                );
            }
        } else {
            logger.info(
                    "Operation [{} - {}] partially completed ({}/{}) for WorkOrder {}",
                    operation.getSequence(),
                    operation.getOperationName(),
                    newCompleted,
                    operation.getPlannedQuantity(),
                    workOrder.getWorkOrderNumber()
            );
        }

        workOrderOperationRepository.save(operation);

        // Update WorkOrder progress
        BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);

        workOrder.setCompletedQuantity(totalCompleted);

        // Update WorkOrder status if first operation being started
        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.RELEASED) {
            workOrder.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
            workOrder.setActualStartDate(new Date());
        }

        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} progress updated: completedQty={}",
                workOrder.getWorkOrderNumber(),
                totalCompleted
        );

        // Audit the operation completion
        auditService.record(
                workOrder,
                WorkOrderEventType.OPERATION_STARTED,
                "operation",
                null,
                operation.getSequence() + " - " + operation.getOperationName() + " (qty: " +
                        partialCompleteDTO.getCompletedQuantity() + ")",
                "Operation partial completion: " + (partialCompleteDTO.getRemarks() != null ?
                        partialCompleteDTO.getRemarks() : "")
        );
    }

    @Transactional
    @Override
    public void completeOperation(Long operationId, BigDecimal completedQty) {

        logger.info("Completing WorkOrderOperation id={} with qty={}", operationId, completedQty);

        //  Fetch operation
        WorkOrderOperation operation =
                workOrderOperationRepository.findById(operationId)
                        .orElseThrow(() -> {
                            logger.error("Operation not found id={}", operationId);
                            return new EntityNotFoundException("Operation not found");
                        });

        WorkOrder workOrder = operation.getWorkOrder();

        //  Validate operation status
        if (operation.getStatus() != OperationStatus.IN_PROGRESS) {
            logger.warn(
                    "Cannot complete operation {} for WorkOrder {} due to status {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    operation.getStatus()
            );
            throw new IllegalStateException("Only IN_PROGRESS operations can be completed");
        }

        //  Validate quantity
        if (completedQty == null || completedQty.compareTo(BigDecimal.ZERO) <= 0) {
            logger.error("Invalid completed quantity {} for operation {}", completedQty, operationId);
            throw new IllegalArgumentException("Completed quantity must be greater than zero");
        }

        if (!Boolean.TRUE.equals(operation.getAllowOverCompletion()) &&
                completedQty.compareTo(operation.getPlannedQuantity()) > 0) {

            logger.warn(
                    "Over-completion not allowed for operation {} (planned={}, completed={})",
                    operation.getSequence(),
                    operation.getPlannedQuantity(),
                    completedQty
            );
            throw new IllegalStateException("Completed quantity exceeds planned quantity");
        }

        //  Complete operation
        operation.setCompletedQuantity(completedQty);
        operation.setStatus(OperationStatus.COMPLETED);
        operation.setActualEndDate(new Date());

        workOrderOperationRepository.save(operation);

        logger.info(
                "Operation [{} - {}] COMPLETED for WorkOrder {}",
                operation.getSequence(),
                operation.getOperationName(),
                workOrder.getWorkOrderNumber()
        );

        // Unlock next operation (if exists)
        WorkOrderOperation nextOperation =
                workOrderOperationRepository
                        .findTopByWorkOrderAndSequenceGreaterThanOrderBySequenceAsc(
                                workOrder, operation.getSequence()
                        );

        if (nextOperation != null) {
            nextOperation.setStatus(OperationStatus.READY);
            workOrderOperationRepository.save(nextOperation);

            logger.info(
                    "Next operation [{} - {}] set to READY for WorkOrder {}",
                    nextOperation.getSequence(),
                    nextOperation.getOperationName(),
                    workOrder.getWorkOrderNumber()
            );
        }

        // Update WorkOrder progress
        BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);

        workOrder.setCompletedQuantity(totalCompleted);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} progress updated: completedQty={}",
                workOrder.getWorkOrderNumber(),
                totalCompleted
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.OPERATION_STARTED,
                "operation",
                null,
                operation.getSequence() + " - " + operation.getOperationName(),
                "Operation started"
        );
    }

    @Transactional
    @Override
    public void completeWorkOrder(int workOrderId) {

        logger.info("Completing WorkOrder id={}", workOrderId);

        //  Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.IN_PROGRESS) {
            logger.warn(
                    "Cannot complete WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "Only IN_PROGRESS WorkOrders can be completed"
            );
        }

        //  Validate all operations completed
        boolean pendingOperations =
                workOrderOperationRepository
                        .existsByWorkOrderAndStatusNot(
                                workOrder, OperationStatus.COMPLETED
                        );

        if (pendingOperations) {
            logger.warn(
                    "Cannot complete WorkOrder {}: not all operations are completed",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException(
                    "All operations must be COMPLETED before completing WorkOrder"
            );
        }

        // Validate that all required material quantities are met (issued + completed)
        List<WorkOrderMaterial> materials = workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());

        for (WorkOrderMaterial material : materials) {
            BigDecimal totalUsed = material.getIssuedQuantity().add(
                    material.getScrappedQuantity() != null ? material.getScrappedQuantity() : BigDecimal.ZERO
            );

            if (totalUsed.compareTo(material.getNetRequiredQuantity()) < 0) {
                logger.warn(
                        "Cannot complete WorkOrder {}: material {} has insufficient issued/scrapped qty. Required: {}, Used: {}",
                        workOrder.getWorkOrderNumber(),
                        material.getComponent().getItemCode(),
                        material.getNetRequiredQuantity(),
                        totalUsed
                );
                throw new IllegalStateException(
                        "All materials must have sufficient issued or scrapped quantities to meet required quantity"
                );
            }
        }

        // Finalize quantities
        BigDecimal totalCompleted = calculateWorkOrderCompletedQuantity(workOrder);

        workOrder.setCompletedQuantity(totalCompleted);

        //  Complete Work Order
        workOrder.setWorkOrderStatus(WorkOrderStatus.COMPLETED);
        workOrder.setActualEndDate(new Date());

        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} COMPLETED successfully with completedQty={}",
                workOrder.getWorkOrderNumber(),
                totalCompleted
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.COMPLETED,
                "status",
                "IN_PROGRESS",
                "COMPLETED",
                "WorkOrder Completed"
        );
    }

    @Transactional
    @Override
    public void closeWorkOrder(int workOrderId) {

        logger.info("Closing WorkOrder id={}", workOrderId);

        // Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.COMPLETED) {
            logger.warn(
                    "Cannot close WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "Only COMPLETED WorkOrders can be closed"
            );
        }

        //  Safety check: no operation IN_PROGRESS (defensive)
        boolean opInProgress =
                workOrderOperationRepository
                        .existsByWorkOrderAndStatus(
                                workOrder, OperationStatus.IN_PROGRESS
                        );

        if (opInProgress) {
            logger.error(
                    "Close blocked: operation still IN_PROGRESS for WorkOrder {}",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException(
                    "Cannot close WorkOrder with operations in progress"
            );
        }

        //  Safety check: no pending materials
        boolean pendingMaterials =
                workOrderMaterialRepository
                        .existsByWorkOrderAndIssueStatusNot(
                                workOrder, MaterialIssueStatus.ISSUED
                        );

        if (pendingMaterials) {
            logger.error(
                    "Close blocked: materials not fully issued for WorkOrder {}",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException(
                    "Cannot close WorkOrder with pending material issues"
            );
        }

        // Close Work Order
        workOrder.setWorkOrderStatus(WorkOrderStatus.CLOSED);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} CLOSED successfully",
                workOrder.getWorkOrderNumber()
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.CLOSED,
                "status",
                "COMPLETED",
                "CLOSED",
                "WorkOrder Closed"
        );
    }

    @Transactional
    @Override
    public void cancelWorkOrder(int workOrderId) {

        logger.info("Cancelling WorkOrder id={}", workOrderId);

        // Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        WorkOrderStatus status = workOrder.getWorkOrderStatus();

        // Status guard
        if (status == WorkOrderStatus.COMPLETED || status == WorkOrderStatus.CLOSED) {
            logger.warn(
                    "Cancel rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    status
            );
            throw new IllegalStateException(
                    "Completed or Closed WorkOrders cannot be cancelled"
            );
        }

        // Ensure no operation IN_PROGRESS
        boolean opInProgress =
                workOrderOperationRepository
                        .existsByWorkOrderAndStatus(
                                workOrder, OperationStatus.IN_PROGRESS
                        );

        if (opInProgress) {
            logger.warn(
                    "Cancel blocked: operation IN_PROGRESS for WorkOrder {}",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException(
                    "Cannot cancel WorkOrder while an operation is in progress"
            );
        }

        // Cancel operations (non-completed)
        List<WorkOrderOperation> operations =
                workOrderOperationRepository
                        .findByWorkOrder(workOrder);

        for (WorkOrderOperation op : operations) {
            if (op.getStatus() != OperationStatus.COMPLETED) {
                op.setStatus(OperationStatus.CANCELLED);
            }
        }

        workOrderOperationRepository.saveAll(operations);

        // Cancel Work Order
        workOrder.setWorkOrderStatus(WorkOrderStatus.CANCELLED);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} CANCELLED successfully",
                workOrder.getWorkOrderNumber()
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.CANCELLED,
                "status",
                status.toString(),
                "CANCELLED",
                "WorkOrder Cancelled"
        );
    }

    @Transactional
    @Override
    public void softDeleteWorkOrder(int workOrderId, String reason) {

        logger.info("Soft deleting WorkOrder id={}", workOrderId);

        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        WorkOrderStatus status = workOrder.getWorkOrderStatus();

        // Status guard
        if (!(status == WorkOrderStatus.CREATED || status == WorkOrderStatus.CANCELLED)) {
            logger.warn(
                    "Soft delete rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    status
            );
            throw new IllegalStateException(
                    "Only CREATED or CANCELLED WorkOrders can be deleted"
            );
        }

        Date now = new Date();

        // Soft delete children first (defensive)
        List<WorkOrderOperation> operations =
                workOrderOperationRepository.findByWorkOrder(workOrder);

        for (WorkOrderOperation op : operations) {
            op.setDeletedDate(now);
        }
        workOrderOperationRepository.saveAll(operations);

        List<WorkOrderMaterial> materials =
                workOrderMaterialRepository.findByWorkOrder(workOrder);

        for (WorkOrderMaterial mat : materials) {
            mat.setDeletedDate(now);
        }
        workOrderMaterialRepository.saveAll(materials);

        //  Soft delete work order
        workOrder.setDeletedDate(now);
        workOrderRepository.save(workOrder);

        // Audit
        auditService.record(
                workOrder,
                WorkOrderEventType.UPDATED,
                "deletedDate",
                null,
                now.toString(),
                "WorkOrder soft deleted: " + reason
        );

        logger.info(
                "WorkOrder {} soft deleted successfully",
                workOrder.getWorkOrderNumber()
        );
    }

    public List<WorkOrderHistoryDTO> getWorkOrderHistory(int workOrderId){
            logger.info("Fetching history for WorkOrder id={}", workOrderId);
            WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                    .orElseThrow(() -> {
                        logger.error("WorkOrder not found id={}", workOrderId);
                        return new EntityNotFoundException("WorkOrder not found");
                    });

            List<WorkOrderHistoryDTO> history = auditService.getHistoryForWorkOrder(
                workOrderId
            );

            logger.info(
                    "History fetched for WorkOrder {}: {} events",
                    workOrder.getWorkOrderNumber(),
                    history.size()
            );
            return history;
    }

}
