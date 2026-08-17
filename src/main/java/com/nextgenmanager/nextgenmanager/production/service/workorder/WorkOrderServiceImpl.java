package com.nextgenmanager.nextgenmanager.production.service.workorder;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperationDependency;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.common.spec.GenericSpecification;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.production.dto.*;
import com.nextgenmanager.nextgenmanager.production.enums.*;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderListMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkOrderMapper;
import com.nextgenmanager.nextgenmanager.bom.model.routing.Routing;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.model.TestTemplate;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderLine;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderTestResult;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.WorkCenterRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderLineRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.model.RejectionEntry;
import com.nextgenmanager.nextgenmanager.production.repository.RejectionEntryRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderTestResultRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialReorderRepository;
import com.nextgenmanager.nextgenmanager.bom.service.routing.RoutingService;
import org.springframework.security.core.context.SecurityContextHolder;
import com.nextgenmanager.nextgenmanager.production.service.audit.WorkOrderAuditService;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
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
    private com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService inventoryInstanceService;

    @Autowired
    private com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionService inventoryTransactionService;

    @Autowired
    private BomService bomService;

    @Autowired
    private WorkCenterRepository workCenterRepository;

    @Autowired
    private TestTemplateService testTemplateService;

    @Autowired
    private WorkOrderTestResultRepository workOrderTestResultRepository;

    @Autowired
    private RejectionEntryRepository rejectionEntryRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository inventoryRequestRepository;

    @Autowired
    private WorkOrderMaterialReorderRepository workOrderMaterialReorderRepository;

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderService.class);

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private WorkOrderListMapper workOrderListMapper;

    @Autowired
    private WorkOrderQaService workOrderQaService;

    @Autowired
    private WorkOrderNumberGenerator workOrderNumberGenerator;

    @Autowired
    private WorkOrderLineRepository workOrderLineRepository;



    @Override
    @Transactional(readOnly = true)
    public WorkOrderDTO getWorkOrder(int id) {

        WorkOrder workOrder = workOrderRepository.getReferenceById(id);
        logger.debug("Fetched Work Order: {}", workOrder.getWorkOrderNumber());
        WorkOrderDTO dto = workOrderMapper.toDTO(workOrder);

        // Fetch InventoryRequest details for materials + reorder summary
        if (dto.getMaterials() != null) {
            for (WorkOrderMaterialDTO mat : dto.getMaterials()) {
                if (mat.getInventoryRequestId() != null) {
                    inventoryRequestRepository.findById(mat.getInventoryRequestId()).ifPresent(req -> {
                        mat.setMrStatus(req.getApprovalStatus().name());
                        mat.setMrApprovedQuantity(req.getApprovedQuantity());
                        mat.setMrRejectionReason(req.getRejectionReason());
                    });
                }
                if (mat.getId() != null) {
                    List<com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterialReorder> reorders =
                            workOrderMaterialReorderRepository.findByWorkOrderMaterialIdOrderByCreatedDateDesc(mat.getId());
                    mat.setReorderCount(reorders.size());
                    BigDecimal approvedReorderQty = reorders.stream()
                            .filter(r -> r.getInventoryRequestId() != null)
                            .map(r -> inventoryRequestRepository.findById(r.getInventoryRequestId()).orElse(null))
                            .filter(r -> r != null
                                    && (r.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.APPROVED
                                    || r.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.PARTIAL))
                            .map(r -> r.getApprovedQuantity() != null ? r.getApprovedQuantity() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    mat.setApprovedReorderQuantity(approvedReorderQty);
                    BigDecimal base = mat.getPlannedRequiredQuantity() != null
                            ? mat.getPlannedRequiredQuantity() : BigDecimal.ZERO;
                    mat.setEffectiveRequiredQuantity(base.add(approvedReorderQty));
                }
            }
        }

        // Enrich operations with pending rejection quantity (one query for the whole WO)
        if (dto.getOperations() != null && !dto.getOperations().isEmpty()) {
            List<RejectionEntry> pending = rejectionEntryRepository
                    .findByWorkOrderIdAndDispositionStatus(id, DispositionStatus.PENDING);
            Map<Long, BigDecimal> pendingByOpId = pending.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            r -> r.getOperation().getId(),
                            java.util.stream.Collectors.reducing(
                                    BigDecimal.ZERO, RejectionEntry::getRejectedQuantity, BigDecimal::add)
                    ));
            for (WorkOrderOperationDTO opDto : dto.getOperations()) {
                opDto.setPendingRejectionQuantity(
                        pendingByOpId.getOrDefault(opDto.getId(), BigDecimal.ZERO));
            }
        }

        return dto;
    }

    private static final Map<String, String> JOIN_FIELD_MAP = Map.of(
            "bomName", "bom.bomName",
            "salesOrderNumber", "salesOrder.orderNumber",
            "workCenter", "workCenter.centerName",
            "parentWorkOrderNumber", "parentWorkOrder.workOrderNumber",
            "status", "workOrderStatus",
            "createdDate", "creationDate"
    );


    @Transactional
    @Override
    public WorkOrderDTO addWorkOrder(WorkOrderRequestDTO dto) {

        // Validate mandatory inputs
        logger.debug("Creating Work Order for BOM ID: {}, Planned Qty: {}",
                dto.getBomId(), dto.getPlannedQuantity());

        // A request either carries explicit lines or the legacy flat single-line fields.
        List<WorkOrderLineRequestDTO> lineRequests = normaliseLineRequests(dto);
        List<ResolvedLine> resolvedLines = new ArrayList<>();
        for (WorkOrderLineRequestDTO lineRequest : lineRequests) {
            resolvedLines.add(resolveLine(lineRequest));
        }



        SalesOrder salesOrder = null;
        if (dto.getSalesOrderId() != null) {
            salesOrder = salesOrderRepository.findById(dto.getSalesOrderId().longValue())
                    .orElseThrow(() -> new EntityNotFoundException("Sales Order not found with ID: " + dto.getSalesOrderId()));
        }

        WorkOrder parentWorkOrder = null;
        if (dto.getParentWorkOrderId() != null) {
            parentWorkOrder = workOrderRepository.findById(dto.getParentWorkOrderId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent WorkOrder not found with ID: " + dto.getParentWorkOrderId()));
        }

        WorkCenter workCenter = null;
        if (dto.getWorkCenterId() != null) {
            workCenter = workCenterRepository.findById(dto.getWorkCenterId())
                    .orElseThrow(() -> new EntityNotFoundException("WorkCenter not found with ID: " + dto.getWorkCenterId()));
        }

        // ── Create Work Order header ────────────────────────────────────────────
        WorkOrder workOrder = new WorkOrder();
        workOrder.setWorkOrderNumber(generateWorkOrderNumber());
        workOrder.setSalesOrder(salesOrder);
        workOrder.setParentWorkOrder(parentWorkOrder);
        workOrder.setWorkCenter(workCenter);

        // Header BOM/routing/quantity are a mirror of the first line, kept only while the
        // DTO layer and UI still read them. The lines are the source of truth.
        ResolvedLine headLine = resolvedLines.get(0);
        workOrder.setBom(headLine.bom());
        workOrder.setRouting(headLine.routing());
        workOrder.setPlannedQuantity(resolvedLines.stream()
                .map(ResolvedLine::plannedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        workOrder.setCompletedQuantity(BigDecimal.ZERO);
        workOrder.setScrappedQuantity(BigDecimal.ZERO);

        workOrder.setPriority(dto.getPriority() != null ? dto.getPriority() : WorkOrderPriority.NORMAL);
        workOrder.setSourceType(dto.getSourceType());
        workOrder.setReferenceDocument(dto.getReferenceDocument());
        workOrder.setRemarks(dto.getRemarks());
        workOrder.setAllowBackflush(dto.isAllowBackflush());

        workOrder.setDueDate(dto.getDueDate());
        workOrder.setPlannedStartDate(dto.getPlannedStartDate());
        workOrder.setPlannedEndDate(dto.getPlannedEndDate());

        workOrder.setWorkOrderStatus(WorkOrderStatus.CREATED);

        // Save header first (ID needed)
        workOrder = workOrderRepository.save(workOrder);

        // ── Explode every line: each gets its own operations, materials and tests ──
        List<WorkOrderOperation> operations = new ArrayList<>();
        List<WorkOrderMaterial> materials = new ArrayList<>();
        List<WorkOrderTestResult> testResults = new ArrayList<>();
        BigDecimal totalProductionMinutes = BigDecimal.ZERO;

        int lineNumber = 1;
        for (ResolvedLine resolved : resolvedLines) {
            WorkOrderLine line = createLine(workOrder, lineNumber++, resolved.bom(), resolved.routing(),
                    resolved.plannedQuantity(),
                    resolved.dueDate() != null ? resolved.dueDate() : dto.getDueDate());
            line.setSalesOrderItemId(resolved.salesOrderItemId());

            totalProductionMinutes = totalProductionMinutes.add(
                    explodeLine(workOrder, line, resolved, operations, materials, testResults));
        }

        // Operations are saved before materials so the material -> operation gate resolves.
        workOrderOperationRepository.saveAll(operations);
        for (WorkOrderOperation op : operations) {
            workOrderQaService.createSnapshotsForOperation(op);
        }
        workOrderMaterialRepository.saveAll(materials);
        if (!testResults.isEmpty()) {
            workOrderTestResultRepository.saveAll(testResults);
            logger.info("Copied {} test templates into WorkOrder {}",
                    testResults.size(), workOrder.getWorkOrderNumber());
        }

        workOrder.setEstimatedProductionMinutes(totalProductionMinutes.setScale(2, RoundingMode.HALF_UP));

        // Attach children to header
        workOrder.setMaterials(materials);
        workOrder.setOperations(operations);
        workOrder.setTestResults(testResults);

        // Save updated estimation
        workOrder = workOrderRepository.save(workOrder);

        // Record audit
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
    @Transactional(readOnly = true)
    public Page<WorkOrderListDTO> getAllWorkOrders(
           FilterRequest request
    ) {

        Sort.Direction direction = Sort.Direction.fromString(request.getSortDir());
        String sortBy = request.getSortBy() != null ? request.getSortBy() : "creationDate";
        sortBy = JOIN_FIELD_MAP.getOrDefault(sortBy, sortBy);
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
        return workOrderNumberGenerator.next();
    }

    /**
     * Header status after a completion attempt.
     *
     * <p>COMPLETED only when every line actually produced its planned quantity. If some lines
     * produced and others did not, the header is IN_PROGRESS so the shortfall stays visible —
     * reporting COMPLETED would hide a line that made nothing. A single-line work order that
     * completed is COMPLETED, exactly as before.
     */
    private WorkOrderStatus deriveHeaderStatus(WorkOrder workOrder) {
        List<WorkOrderLine> lines = workOrder.activeLines();
        if (lines.isEmpty()) {
            return WorkOrderStatus.COMPLETED;
        }
        boolean allComplete = lines.stream().allMatch(l -> {
            BigDecimal planned = l.getPlannedQuantity() != null ? l.getPlannedQuantity() : BigDecimal.ZERO;
            BigDecimal done = l.getCompletedQuantity() != null ? l.getCompletedQuantity() : BigDecimal.ZERO;
            return done.compareTo(planned) >= 0;
        });
        return allComplete ? WorkOrderStatus.COMPLETED : WorkOrderStatus.IN_PROGRESS;
    }

    /** A line request with its BOM and routing resolved and validated. */
    private record ResolvedLine(Bom bom, Routing routing, BigDecimal plannedQuantity,
                                Date dueDate, Long salesOrderItemId) {}

    /**
     * Reads the requested lines, falling back to the flat single-line fields when none are given
     * so that existing callers (and the auto-routing in procurement) keep working unchanged.
     */
    private List<WorkOrderLineRequestDTO> normaliseLineRequests(WorkOrderRequestDTO dto) {
        if (dto.getLines() != null && !dto.getLines().isEmpty()) {
            return dto.getLines();
        }
        WorkOrderLineRequestDTO single = new WorkOrderLineRequestDTO();
        single.setBomId(dto.getBomId());
        single.setRoutingId(dto.getRoutingId());
        single.setPlannedQuantity(dto.getPlannedQuantity());
        single.setDueDate(dto.getDueDate());
        return List.of(single);
    }

    /** Validates one requested line and resolves its BOM and routing. */
    private ResolvedLine resolveLine(WorkOrderLineRequestDTO request) {
        if (request.getBomId() == null) {
            throw new IllegalArgumentException("BOM ID is required to create Work Order");
        }
        if (request.getPlannedQuantity() == null
                || request.getPlannedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Planned quantity must be greater than zero");
        }

        Bom bom = bomService.getBom(request.getBomId());
        if (bom == null) {
            throw new EntityNotFoundException("BOM not found with ID: " + request.getBomId());
        }
        if (bom.getBomStatus() != com.nextgenmanager.nextgenmanager.bom.model.BomStatus.APPROVED
                && bom.getBomStatus() != com.nextgenmanager.nextgenmanager.bom.model.BomStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot create Work Order: BOM '" + bom.getBomName() +
                            "' is " + bom.getBomStatus() + ". Only APPROVED or ACTIVE BOMs are allowed.");
        }

        Routing routing = routingService.getRoutingEntityByBom(bom.getId());
        if (routing == null) {
            throw new EntityNotFoundException("No routing found for BOM ID: " + request.getBomId());
        }

        if (bom.getPositions().isEmpty() && routing.getOperations().isEmpty()) {
            throw new IllegalStateException(
                    "BOM has no Material OR Operations for BOM ID: " + bom.getId());
        }

        return new ResolvedLine(bom, routing, request.getPlannedQuantity(),
                request.getDueDate(), request.getSalesOrderItemId());
    }

    /**
     * Explodes one line's routing into operations and its BOM into materials, and copies the
     * test templates for the item it makes. Everything created here is scoped to {@code line};
     * the accumulator lists are shared only so the caller can save in two batches.
     *
     * @return the estimated production minutes for this line
     */
    private BigDecimal explodeLine(WorkOrder workOrder, WorkOrderLine line, ResolvedLine resolved,
                                   List<WorkOrderOperation> operations,
                                   List<WorkOrderMaterial> materials,
                                   List<WorkOrderTestResult> testResults) {
        BigDecimal plannedQty = resolved.plannedQuantity();
        BigDecimal lineMinutes = BigDecimal.ZERO;

        // ── Routing → WorkOrderOperation ────────────────────────────────────────
        List<WorkOrderOperation> lineOperations = new ArrayList<>();
        for (RoutingOperation routingOp : resolved.routing().getOperations()) {
            WorkOrderOperation woo = new WorkOrderOperation();
            woo.setWorkOrder(workOrder);
            woo.setWorkOrderLine(line);
            woo.setRoutingOperation(routingOp);
            woo.setSequence(routingOp.getSequenceNumber());
            woo.setOperationName(routingOp.getName());
            woo.setWorkCenter(routingOp.getWorkCenter());

            woo.setPlannedQuantity(plannedQty);
            woo.setCompletedQuantity(BigDecimal.ZERO);
            woo.setScrappedQuantity(BigDecimal.ZERO);
            woo.setStatus(OperationStatus.PLANNED);

            if (routingOp.getMachineDetails() != null) {
                woo.setAssignedMachine(routingOp.getMachineDetails());
            }
            lineOperations.add(woo);

            BigDecimal setupTime = routingOp.getSetupTime() != null ? routingOp.getSetupTime() : BigDecimal.ZERO;
            BigDecimal runTime = routingOp.getRunTime() != null ? routingOp.getRunTime() : BigDecimal.ZERO;
            lineMinutes = lineMinutes.add(setupTime.add(runTime.multiply(plannedQty)));
        }

        // The first operation of THIS line opens with the line's quantity available.
        lineOperations.sort(Comparator.comparingInt(WorkOrderOperation::getSequence));
        WorkOrderOperation firstOperation = null;
        if (!lineOperations.isEmpty()) {
            firstOperation = lineOperations.get(0);
            firstOperation.setAvailableInputQuantity(plannedQty);
            firstOperation.setStatus(OperationStatus.READY);
        }
        operations.addAll(lineOperations);

        Map<Long, WorkOrderOperation> routingOpToWoo = new HashMap<>();
        for (WorkOrderOperation woo : lineOperations) {
            if (woo.getRoutingOperation() != null) {
                routingOpToWoo.put(woo.getRoutingOperation().getId(), woo);
            }
        }

        // ── BOM → WorkOrderMaterial ─────────────────────────────────────────────
        for (BomPosition bomItem : resolved.bom().getPositions()) {
            WorkOrderMaterial wom = new WorkOrderMaterial();
            wom.setWorkOrder(workOrder);
            wom.setWorkOrderLine(line);
            wom.setComponent(bomItem.getChildInventoryItem());

            BigDecimal baseQty = BigDecimal.valueOf(bomItem.getQuantity());
            BigDecimal scrapPercent = bomItem.getScrapPercentage() != null
                    ? bomItem.getScrapPercentage() : BigDecimal.ZERO;

            BigDecimal netRequired = baseQty.multiply(plannedQty).setScale(5, RoundingMode.HALF_UP);
            BigDecimal scrapMultiplier = BigDecimal.ONE.add(
                    scrapPercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
            BigDecimal plannedRequired = netRequired.multiply(scrapMultiplier)
                    .setScale(5, RoundingMode.HALF_UP);

            wom.setNetRequiredQuantity(netRequired);
            wom.setPlannedRequiredQuantity(plannedRequired);
            wom.setScrappercent(scrapPercent);
            wom.setIssuedQuantity(BigDecimal.ZERO);
            wom.setScrappedQuantity(BigDecimal.ZERO);
            wom.setIssueStatus(MaterialIssueStatus.NOT_ISSUED);

            // Material issue gate. The lookup is scoped to THIS line's operations, so a BOM that
            // shares a routing operation id with another line still gates on its own operation.
            if (bomItem.getRoutingOperation() != null) {
                WorkOrderOperation linkedOp = routingOpToWoo.get(bomItem.getRoutingOperation().getId());
                if (linkedOp != null) {
                    wom.setWorkOrderOperation(linkedOp);
                } else {
                    logger.warn("BOM routing op ID {} not found in line {} ops for material {}, "
                                    + "falling back to first operation",
                            bomItem.getRoutingOperation().getId(), line.getLineNumber(),
                            wom.getComponent().getName());
                    wom.setWorkOrderOperation(firstOperation);
                }
            } else {
                wom.setWorkOrderOperation(firstOperation);
            }
            materials.add(wom);
        }

        // ── TestTemplates → WorkOrderTestResult (for the item this line makes) ──
        if (line.getInventoryItem() != null) {
            for (TestTemplate tmpl : testTemplateService.getActiveTemplatesForItem(
                    line.getInventoryItem().getInventoryItemId())) {
                WorkOrderTestResult tr = new WorkOrderTestResult();
                tr.setWorkOrder(workOrder);
                tr.setTestTemplate(tmpl);
                tr.setTestName(tmpl.getTestName());
                tr.setInspectionType(tmpl.getInspectionType());
                tr.setSampleSize(tmpl.getSampleSize());
                tr.setIsMandatory(tmpl.getIsMandatory());
                tr.setSequence(tmpl.getSequence());
                tr.setAcceptanceCriteria(tmpl.getAcceptanceCriteria());
                tr.setUnitOfMeasure(tmpl.getUnitOfMeasure());
                tr.setMinValue(tmpl.getMinValue());
                tr.setMaxValue(tmpl.getMaxValue());
                tr.setResult(com.nextgenmanager.nextgenmanager.production.enums.TestResult.PENDING);
                testResults.add(tr);
            }
        }

        return lineMinutes;
    }

    /**
     * Creates one {@link WorkOrderLine} — the unit of manufacture — on a work order.
     *
     * <p>The produced item is taken from the BOM's parent item at creation time and stored on the
     * line explicitly, so the line keeps its identity if the BOM is later revised.
     *
     * <p>Multiple lines are allowed: consumers resolve the produced item, BOM and cost from the
     * line rather than from the work order, so each line is produced and valued on its own.
     */
    private WorkOrderLine createLine(WorkOrder workOrder, int lineNumber, Bom bom, Routing routing,
                                     BigDecimal plannedQuantity, Date dueDate) {
        WorkOrderLine line = new WorkOrderLine();
        line.setWorkOrder(workOrder);
        line.setLineNumber(lineNumber);
        line.setBom(bom);
        line.setRouting(routing);
        line.setInventoryItem(bom != null ? bom.getParentInventoryItem() : null);
        line.setPlannedQuantity(plannedQuantity);
        line.setCompletedQuantity(BigDecimal.ZERO);
        line.setScrappedQuantity(BigDecimal.ZERO);
        line.setStatus(WorkOrderStatus.CREATED);
        line.setDueDate(dueDate);
        return workOrderLineRepository.save(line);
    }

    @Override
    @Transactional(readOnly = true)
    public String nextNumber() {
        return workOrderNumberGenerator.preview();
    }

    /** A finished or abandoned work order has no future work left to move. */
    private static final Set<WorkOrderStatus> SPLIT_BLOCKED_STATUSES = EnumSet.of(
            WorkOrderStatus.COMPLETED,
            WorkOrderStatus.CLOSED,
            WorkOrderStatus.SHORT_CLOSED,
            WorkOrderStatus.CANCELLED);

    /**
     * Splits quantity off a work order into a new one.
     *
     * <p>Every rule below follows from one principle: <b>a split moves future work, never past
     * work.</b>
     *
     * <ul>
     *   <li><b>Produced units stay.</b> A line can give up its planned quantity less the most any
     *       of its operations has already completed. Units that exist were made by this work
     *       order, and its cost postings and inventory movements already say so.
     *   <li><b>Issued material stays.</b> Stock on the floor was issued against this work order;
     *       moving it is a physical inventory transfer, not a bookkeeping edit. The source's
     *       requirements scale down to what it retains, so material issued beyond the reduced
     *       requirement surfaces as issued-above-required and can be returned to stores as a
     *       deliberate act.
     *   <li><b>The new work order starts at CREATED.</b> It raises its own material requests and
     *       goes through the normal release, rather than inheriting approvals that were granted
     *       for a quantity no longer on the source.
     *   <li><b>Something must remain on every line it touches.</b> Moving a line's entire quantity
     *       is handing the item to another work order, which is a different operation from
     *       splitting a quantity.
     * </ul>
     */
    @Transactional
    @Override
    public WorkOrderDTO splitWorkOrder(int workOrderId, WorkOrderSplitRequestDTO dto) {

        WorkOrder source = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found"));

        if (dto == null || dto.getLines() == null || dto.getLines().isEmpty()) {
            throw new IllegalArgumentException("At least one line quantity is required to split.");
        }
        if (SPLIT_BLOCKED_STATUSES.contains(source.getWorkOrderStatus())) {
            throw new IllegalStateException("WorkOrder " + source.getWorkOrderNumber() + " is "
                    + source.getWorkOrderStatus() + " — it has no remaining work to split.");
        }

        List<WorkOrderOperation> sourceOperations = workOrderOperationRepository.findByWorkOrder(source);
        List<WorkOrderMaterial> sourceMaterials = workOrderMaterialRepository.findByWorkOrder(source);

        Map<Long, WorkOrderLine> linesById = new HashMap<>();
        source.activeLines().forEach(l -> linesById.put(l.getId(), l));

        // ── Validate every requested move before mutating anything ──────────────
        Map<WorkOrderLine, BigDecimal> moves = new LinkedHashMap<>();
        for (WorkOrderSplitRequestDTO.LineSplit request : dto.getLines()) {
            WorkOrderLine line = linesById.get(request.getLineId());
            if (line == null) {
                throw new EntityNotFoundException("Line " + request.getLineId()
                        + " does not belong to WorkOrder " + source.getWorkOrderNumber());
            }
            if (moves.containsKey(line)) {
                throw new IllegalArgumentException("Line " + line.getLineNumber()
                        + " is listed more than once in the split.");
            }

            BigDecimal moveQty = request.getQuantity();
            if (moveQty == null || moveQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Split quantity for line " + line.getLineNumber()
                        + " must be greater than zero.");
            }

            List<WorkOrderOperation> lineOperations = operationsOfLine(sourceOperations, line);
            BigDecimal produced = highestCompletedQuantity(lineOperations)
                    .max(line.getCompletedQuantity() != null
                            ? line.getCompletedQuantity() : BigDecimal.ZERO);
            BigDecimal movable = line.getPlannedQuantity().subtract(produced);

            if (moveQty.compareTo(movable) > 0) {
                throw new IllegalStateException(String.format(
                        "Line %d (%s) can give up at most %s of its %s — %s already produced.",
                        line.getLineNumber(), lineItemLabel(line), movable.stripTrailingZeros().toPlainString(),
                        line.getPlannedQuantity().stripTrailingZeros().toPlainString(),
                        produced.stripTrailingZeros().toPlainString()));
            }
            // A line may move in full — it leaves the source entirely. Because movable already
            // excludes produced units, this can only happen when the line has made nothing.
            // Material issued against it is the remaining obstacle: the line's rows go with it,
            // and stock issued to a line that no longer exists would be orphaned on the floor.
            if (line.getPlannedQuantity().subtract(moveQty).compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal issued = materialsOfLine(sourceMaterials, line).stream()
                        .map(m -> m.getIssuedQuantity() != null ? m.getIssuedQuantity() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (issued.compareTo(BigDecimal.ZERO) > 0) {
                    throw new IllegalStateException(String.format(
                            "Line %d (%s) has material issued against it, so its whole quantity "
                            + "cannot be moved. Return the issued material first, or move less "
                            + "than %s.", line.getLineNumber(), lineItemLabel(line),
                            line.getPlannedQuantity().stripTrailingZeros().toPlainString()));
                }
            }
            if (line.getBom() == null || line.getRouting() == null) {
                throw new IllegalStateException("Line " + line.getLineNumber()
                        + " has no BOM or routing to explode into the new work order.");
            }

            moves.put(line, moveQty);
        }

        // The constraint is on the work order, not on each line: an individual item may leave
        // entirely, but the source has to go on making something. A split that empties it is a
        // cancellation wearing a different hat, and would leave a work order whose operations and
        // material requests describe nothing.
        boolean sourceKeepsWork = source.activeLines().stream().anyMatch(line ->
                line.getPlannedQuantity()
                        .subtract(moves.getOrDefault(line, BigDecimal.ZERO))
                        .compareTo(BigDecimal.ZERO) > 0);
        if (!sourceKeepsWork) {
            throw new IllegalStateException("This would move every item off "
                    + source.getWorkOrderNumber() + ", leaving nothing to make. Keep quantity on "
                    + "at least one item, or cancel the work order instead.");
        }

        // ── Build the new work order ────────────────────────────────────────────
        WorkOrder split = new WorkOrder();
        split.setWorkOrderNumber(generateWorkOrderNumber());
        split.setSplitFromWorkOrderId(source.getId());
        split.setSalesOrder(source.getSalesOrder());
        // The work order it came out of is its origin, so that is what it points at — not the
        // source's own parent. splitFromWorkOrderId still records that the link was made by a
        // split rather than by someone raising a sub-assembly order by hand.
        split.setParentWorkOrder(source);
        split.setSourceType(WorkOrderSourceType.PARENT_WORK_ORDER);
        split.setWorkCenter(source.getWorkCenter());
        split.setBom(source.getBom());
        split.setRouting(source.getRouting());
        split.setPriority(source.getPriority());
        split.setAllowBackflush(source.isAllowBackflush());
        split.setDueDate(dto.getDueDate() != null ? dto.getDueDate() : source.getDueDate());
        split.setPlannedStartDate(source.getPlannedStartDate());
        split.setPlannedEndDate(source.getPlannedEndDate());
        split.setRemarks(dto.getRemarks());
        split.setPlannedQuantity(moves.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        split.setCompletedQuantity(BigDecimal.ZERO);
        split.setScrappedQuantity(BigDecimal.ZERO);
        split.setWorkOrderStatus(WorkOrderStatus.CREATED);
        split = workOrderRepository.save(split);

        List<WorkOrderOperation> newOperations = new ArrayList<>();
        List<WorkOrderMaterial> newMaterials = new ArrayList<>();
        List<WorkOrderTestResult> newTestResults = new ArrayList<>();
        BigDecimal splitMinutes = BigDecimal.ZERO;

        int lineNumber = 1;
        for (Map.Entry<WorkOrderLine, BigDecimal> entry : moves.entrySet()) {
            WorkOrderLine sourceLine = entry.getKey();
            BigDecimal moveQty = entry.getValue();

            WorkOrderLine newLine = createLine(split, lineNumber++, sourceLine.getBom(),
                    sourceLine.getRouting(), moveQty,
                    dto.getDueDate() != null ? dto.getDueDate() : sourceLine.getDueDate());
            newLine.setSalesOrderItemId(sourceLine.getSalesOrderItemId());
            newLine.setSplitFromLineId(sourceLine.getId());
            workOrderLineRepository.save(newLine);

            ResolvedLine resolved = new ResolvedLine(sourceLine.getBom(), sourceLine.getRouting(),
                    moveQty, newLine.getDueDate(), sourceLine.getSalesOrderItemId());
            splitMinutes = splitMinutes.add(
                    explodeLine(split, newLine, resolved, newOperations, newMaterials, newTestResults));
        }

        workOrderOperationRepository.saveAll(newOperations);
        for (WorkOrderOperation op : newOperations) {
            workOrderQaService.createSnapshotsForOperation(op);
        }
        workOrderMaterialRepository.saveAll(newMaterials);
        if (!newTestResults.isEmpty()) {
            workOrderTestResultRepository.saveAll(newTestResults);
        }

        split.setEstimatedProductionMinutes(splitMinutes.setScale(2, RoundingMode.HALF_UP));
        split.setMaterials(newMaterials);
        split.setOperations(newOperations);
        split.setTestResults(newTestResults);
        split = workOrderRepository.save(split);

        // ── Shrink the source to what it kept ───────────────────────────────────
        Date splitAt = new Date();
        for (Map.Entry<WorkOrderLine, BigDecimal> entry : moves.entrySet()) {
            WorkOrderLine line = entry.getKey();
            BigDecimal originalQty = line.getPlannedQuantity();
            BigDecimal retained = originalQty.subtract(entry.getValue());

            // The whole line moved: retire it here rather than leaving a line planning nothing,
            // with operations and material requirements for a quantity of zero. Its line number
            // is not reused — uq_workOrderLine_wo_line still holds it, and renumbering would
            // rewrite the identity of lines that never moved.
            if (retained.compareTo(BigDecimal.ZERO) == 0) {
                operationsOfLine(sourceOperations, line).forEach(op -> {
                    op.setDeletedDate(splitAt);
                    workOrderOperationRepository.save(op);
                });
                materialsOfLine(sourceMaterials, line).forEach(material -> {
                    material.setDeletedDate(splitAt);
                    workOrderMaterialRepository.save(material);
                });
                line.setDeletedDate(splitAt);
                workOrderLineRepository.save(line);
                logger.info("Line {} ({}) moved in full from WorkOrder {} to {}",
                        line.getLineNumber(), lineItemLabel(line),
                        source.getWorkOrderNumber(), split.getWorkOrderNumber());
                continue;
            }

            line.setPlannedQuantity(retained);
            workOrderLineRepository.save(line);

            for (WorkOrderOperation op : operationsOfLine(sourceOperations, line)) {
                op.setPlannedQuantity(retained);
                // Input already forwarded can exceed what the operation now plans to make; hold it
                // to the retained quantity, never below what it has already consumed as output.
                if (op.getAvailableInputQuantity().compareTo(retained) > 0) {
                    op.setAvailableInputQuantity(retained.max(op.getCompletedQuantity()));
                }
                workOrderOperationRepository.save(op);
            }

            BigDecimal ratio = retained.divide(originalQty, 10, RoundingMode.HALF_UP);
            for (WorkOrderMaterial material : materialsOfLine(sourceMaterials, line)) {
                if (material.getNetRequiredQuantity() != null) {
                    material.setNetRequiredQuantity(material.getNetRequiredQuantity()
                            .multiply(ratio).setScale(5, RoundingMode.HALF_UP));
                }
                if (material.getPlannedRequiredQuantity() != null) {
                    material.setPlannedRequiredQuantity(material.getPlannedRequiredQuantity()
                            .multiply(ratio).setScale(5, RoundingMode.HALF_UP));
                }
                workOrderMaterialRepository.save(material);
            }
        }

        source.setPlannedQuantity(source.activeLines().stream()
                .map(WorkOrderLine::getPlannedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        source.setEstimatedProductionMinutes(
                estimatedMinutesOf(sourceOperations).setScale(2, RoundingMode.HALF_UP));
        source.setCompletedQuantity(refreshCompletedQuantities(source));
        workOrderRepository.save(source);

        String note = String.format("Split %s off to %s",
                moves.entrySet().stream()
                        .map(e -> e.getValue().stripTrailingZeros().toPlainString()
                                + " × " + lineItemLabel(e.getKey()))
                        .collect(java.util.stream.Collectors.joining(", ")),
                split.getWorkOrderNumber());

        auditService.record(source, WorkOrderEventType.SPLIT, "plannedQuantity", null, null,
                dto.getRemarks() != null && !dto.getRemarks().isBlank()
                        ? note + " — " + dto.getRemarks() : note);
        auditService.record(split, WorkOrderEventType.CREATED, null, null, null,
                "Created by splitting " + source.getWorkOrderNumber());

        logger.info("WorkOrder {} split → {} ({})",
                source.getWorkOrderNumber(), split.getWorkOrderNumber(), note);

        return workOrderMapper.toDTO(split);
    }

    /**
     * Every work order linked to this one, in one flat list — what it came out of, and what came
     * out of it.
     *
     * <p>A link is a link regardless of how it was made: raising a sub-assembly order by hand and
     * splitting quantity off both write {@code parentWorkOrder}, so both surface here. The
     * relation on each row is what distinguishes them, not whether the row appears at all.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RelatedWorkOrderDTO> getRelatedWorkOrders(int workOrderId) {

        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found"));

        Map<Integer, RelatedWorkOrderDTO> related = new LinkedHashMap<>();

        WorkOrder parent = workOrder.getParentWorkOrder();
        if (parent != null && parent.getDeletedDate() == null) {
            boolean bySplit = workOrder.getSplitFromWorkOrderId() != null
                    && workOrder.getSplitFromWorkOrderId() == parent.getId();
            related.put(parent.getId(), toRelatedWorkOrder(parent,
                    bySplit ? RelatedWorkOrderDTO.Relation.SPLIT_PARENT
                            : RelatedWorkOrderDTO.Relation.PARENT));
        }

        // A split made before splits set parentWorkOrder still knows where it came from.
        Integer splitFrom = workOrder.getSplitFromWorkOrderId();
        if (splitFrom != null && !related.containsKey(splitFrom)) {
            workOrderRepository.findById(splitFrom)
                    .filter(wo -> wo.getDeletedDate() == null)
                    .ifPresent(wo -> related.put(wo.getId(),
                            toRelatedWorkOrder(wo, RelatedWorkOrderDTO.Relation.SPLIT_PARENT)));
        }

        for (WorkOrder child : workOrderRepository
                .findByParentWorkOrderIdAndDeletedDateIsNullOrderByIdAsc(workOrderId)) {
            boolean bySplit = child.getSplitFromWorkOrderId() != null
                    && child.getSplitFromWorkOrderId() == workOrderId;
            related.putIfAbsent(child.getId(), toRelatedWorkOrder(child,
                    bySplit ? RelatedWorkOrderDTO.Relation.SPLIT_CHILD
                            : RelatedWorkOrderDTO.Relation.CHILD));
        }

        // Same again for splits that never wrote the parent link.
        for (WorkOrder child : workOrderRepository
                .findBySplitFromWorkOrderIdAndDeletedDateIsNullOrderByIdAsc(workOrderId)) {
            related.putIfAbsent(child.getId(),
                    toRelatedWorkOrder(child, RelatedWorkOrderDTO.Relation.SPLIT_CHILD));
        }

        return related.values().stream()
                .sorted(Comparator.comparing((RelatedWorkOrderDTO r) -> r.getRelation().ordinal())
                        .thenComparingInt(RelatedWorkOrderDTO::getId))
                .toList();
    }

    private RelatedWorkOrderDTO toRelatedWorkOrder(WorkOrder workOrder,
                                                   RelatedWorkOrderDTO.Relation relation) {
        String items = workOrder.activeLines().stream()
                .map(this::lineItemLabel)
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));

        return new RelatedWorkOrderDTO(
                workOrder.getId(),
                workOrder.getWorkOrderNumber(),
                workOrder.getWorkOrderStatus(),
                relation,
                items,
                workOrder.getPlannedQuantity(),
                workOrder.getCompletedQuantity(),
                workOrder.getDueDate());
    }

    private List<WorkOrderOperation> operationsOfLine(List<WorkOrderOperation> operations,
                                                     WorkOrderLine line) {
        return operations.stream()
                .filter(o -> o.getDeletedDate() == null)
                .filter(o -> belongsToLine(o.getWorkOrderLine(), line))
                .toList();
    }

    private List<WorkOrderMaterial> materialsOfLine(List<WorkOrderMaterial> materials,
                                                    WorkOrderLine line) {
        return materials.stream()
                .filter(m -> m.getDeletedDate() == null)
                .filter(m -> belongsToLine(m.getWorkOrderLine(), line))
                .toList();
    }

    /** The most any single operation has completed — the floor below which a line cannot shrink. */
    private BigDecimal highestCompletedQuantity(List<WorkOrderOperation> lineOperations) {
        return lineOperations.stream()
                .map(op -> op.getCompletedQuantity() != null
                        ? op.getCompletedQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::max);
    }

    private BigDecimal estimatedMinutesOf(List<WorkOrderOperation> operations) {
        BigDecimal minutes = BigDecimal.ZERO;
        for (WorkOrderOperation op : operations) {
            if (op.getDeletedDate() != null || op.getRoutingOperation() == null) continue;
            BigDecimal setup = op.getRoutingOperation().getSetupTime() != null
                    ? op.getRoutingOperation().getSetupTime() : BigDecimal.ZERO;
            BigDecimal run = op.getRoutingOperation().getRunTime() != null
                    ? op.getRoutingOperation().getRunTime() : BigDecimal.ZERO;
            minutes = minutes.add(setup.add(run.multiply(op.getPlannedQuantity())));
        }
        return minutes;
    }

    private String lineItemLabel(WorkOrderLine line) {
        return line.getInventoryItem() != null
                ? line.getInventoryItem().getItemCode() : "line " + line.getLineNumber();
    }

    /**
     * Adds finished goods to stock for every line of a work order, each valued at its OWN cost.
     *
     * <p>For each line the material and operation costs of that line are pooled, divided by that
     * line's completed quantity, and posted as a single PRODUCE movement for the line's item.
     * Costs never cross line boundaries: pooling them work-order-wide would value each item at
     * the blended average of all items on the order.
     *
     * <p>Every movement still carries the work order number as its reference document, so the
     * existing GL posting (Dr Finished Goods / Cr WIP, keyed per inventory-ledger row) continues
     * to work unchanged and WIP still nets to zero for the work order as a whole.
     *
     * @return total units produced across all lines
     */
    private BigDecimal produceFinishedGoodsPerLine(WorkOrder workOrder,
                                                   List<WorkOrderMaterial> materials,
                                                   List<WorkOrderOperation> operations) {
        BigDecimal producedTotal = BigDecimal.ZERO;

        for (WorkOrderLine line : workOrder.activeLines()) {
            List<WorkOrderMaterial> lineMaterials = materials.stream()
                    .filter(m -> m.getDeletedDate() == null)
                    .filter(m -> belongsToLine(m.getWorkOrderLine(), line))
                    .toList();
            List<WorkOrderOperation> lineOperations = operations.stream()
                    .filter(o -> o.getDeletedDate() == null)
                    .filter(o -> belongsToLine(o.getWorkOrderLine(), line))
                    .toList();

            BigDecimal lineCompleted = calculateLineCompletedQuantity(line, lineOperations, lineMaterials);
            line.setCompletedQuantity(lineCompleted);
            line.setStatus(WorkOrderStatus.COMPLETED);
            workOrderLineRepository.save(line);

            if (lineCompleted.compareTo(BigDecimal.ZERO) <= 0) {
                logger.info("Line {} of WorkOrder {} completed 0 units — nothing produced",
                        line.getLineNumber(), workOrder.getWorkOrderNumber());
                continue;
            }

            BigDecimal lineCost = materialCostOf(lineMaterials).add(operationCostOf(lineOperations));
            BigDecimal unitCost = lineCost.divide(lineCompleted, 2, RoundingMode.HALF_UP);

            InventoryItem produced = line.getInventoryItem();
            if (produced == null) {
                logger.error("Line {} of WorkOrder {} has no inventory item — cannot produce stock",
                        line.getLineNumber(), workOrder.getWorkOrderNumber());
                continue;
            }

            InventoryTransactionDTO produceDto = new InventoryTransactionDTO();
            produceDto.setInventoryItemId(produced.getInventoryItemId());
            produceDto.setQuantity(lineCompleted.doubleValue());
            produceDto.setCostPerUnit(unitCost.doubleValue());
            produceDto.setTransactionType("PRODUCE");
            produceDto.setReferenceType("WORK_ORDER");
            produceDto.setReferenceDocNo(workOrder.getWorkOrderNumber());
            try {
                produceDto.setCreatedBy(
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication().getName());
            } catch (Exception ignored) { }
            inventoryTransactionService.produceStock(produceDto);
            producedTotal = producedTotal.add(lineCompleted);

            logger.info("PRODUCE {} units of {} for WorkOrder {} line {} at unit cost {}",
                    lineCompleted, produced.getItemCode(), workOrder.getWorkOrderNumber(),
                    line.getLineNumber(), unitCost);

            // Auto-reserve this line's output against the linked Sales Order.
            if (workOrder.getSalesOrder() != null) {
                try {
                    InventoryTransactionDTO reserveDto = new InventoryTransactionDTO();
                    reserveDto.setInventoryItemId(produced.getInventoryItemId());
                    reserveDto.setQuantity(lineCompleted.doubleValue());
                    reserveDto.setTransactionType("RESERVE");
                    reserveDto.setReferenceDocNo(workOrder.getSalesOrder().getOrderNumber());
                    inventoryTransactionService.reserveStock(reserveDto);
                    logger.info("Auto-reserved {} units of {} for SalesOrder {}",
                            lineCompleted, produced.getItemCode(),
                            workOrder.getSalesOrder().getOrderNumber());
                } catch (Exception e) {
                    logger.error("Auto-reserve for SalesOrder failed (WO {} line {}): {}",
                            workOrder.getWorkOrderNumber(), line.getLineNumber(), e.getMessage());
                }
            }
        }
        return producedTotal;
    }

    /**
     * Whether a child belongs to the given line. Children backfilled by V150 always carry a line;
     * a null is treated as belonging to the line being processed so that a single-line work order
     * whose children predate the backfill still produces stock rather than silently skipping.
     */
    private boolean belongsToLine(WorkOrderLine childLine, WorkOrderLine line) {
        return childLine == null || Objects.equals(childLine.getId(), line.getId());
    }

    /** Actual material cost of a line: consumed quantity × the component's standard cost. */
    private BigDecimal materialCostOf(List<WorkOrderMaterial> lineMaterials) {
        BigDecimal total = BigDecimal.ZERO;
        for (WorkOrderMaterial material : lineMaterials) {
            BigDecimal consumed = material.getConsumedQuantity() != null
                    ? material.getConsumedQuantity() : BigDecimal.ZERO;
            double unitCost = Optional.ofNullable(material.getComponent())
                    .map(InventoryItem::getProductFinanceSettings)
                    .map(ProductFinanceSettings::getStandardCost)
                    .orElse(0.0);
            total = total.add(BigDecimal.valueOf(unitCost).multiply(consumed));
        }
        return total;
    }

    /** Actual machine cost of a line's operations, loaded with each work centre's overhead. */
    private BigDecimal operationCostOf(List<WorkOrderOperation> lineOperations) {
        BigDecimal total = BigDecimal.ZERO;
        for (WorkOrderOperation op : lineOperations) {
            if (op.getWorkCenter() == null || op.getWorkCenter().getMachineCostPerHour() == null) {
                continue;
            }
            BigDecimal hoursPerUnit = BigDecimal.ZERO;
            if (op.getRoutingOperation() != null && op.getRoutingOperation().getRunTime() != null) {
                hoursPerUnit = op.getRoutingOperation().getRunTime()
                        .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
            }
            BigDecimal opCost = hoursPerUnit
                    .multiply(op.getCompletedQuantity())
                    .multiply(op.getWorkCenter().getMachineCostPerHour());

            if (op.getWorkCenter().getOverheadPercentage() != null) {
                BigDecimal overheadFactor = BigDecimal.ONE.add(
                        op.getWorkCenter().getOverheadPercentage()
                                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                opCost = opCost.multiply(overheadFactor);
            }
            total = total.add(opCost);
        }
        return total;
    }

    /**
     * The one line a flat, work-order-level edit refers to.
     *
     * @throws IllegalStateException if the work order has anything other than exactly one line —
     *         a flat bomId/routingId/quantity is ambiguous across several lines, so the caller
     *         must use the per-line endpoints instead.
     */
    private WorkOrderLine singleEditableLine(WorkOrder workOrder) {
        List<WorkOrderLine> active = workOrder.activeLines();
        if (active.size() != 1) {
            throw new IllegalStateException(
                    "WorkOrder " + workOrder.getWorkOrderNumber() + " has " + active.size()
                            + " lines, so a single planned quantity is ambiguous — it cannot be "
                            + "changed from the work-order level. Its other details can still be "
                            + "updated.");
        }
        return active.get(0);
    }

    /**
     * Recomputes every active line's completed quantity from its own operations and materials,
     * persists it, and returns the sum — the work order's completed quantity.
     *
     * <p>Two things this replaces. The line figures used to be written only when the work order
     * was completed or closed, so the lines of an in-flight order reported zero produced however
     * far their operations had actually run. And the work-order total was taken from the single
     * highest-sequence operation across every line, which on a multi-line order reports one
     * line's output as the whole order's — finishing one item moved the header quantity as if
     * the other had been made too.
     *
     * <p>Both collapse to the original behaviour on a single-line work order: one line, whose
     * completion is the order's.
     */
    private BigDecimal refreshCompletedQuantities(WorkOrder workOrder) {
        List<WorkOrderOperation> operations = workOrderOperationRepository.findByWorkOrder(workOrder);
        List<WorkOrderMaterial> materials = workOrderMaterialRepository.findByWorkOrder(workOrder);
        List<WorkOrderLine> lines = workOrder.activeLines();

        if (lines.isEmpty()) {
            // Defensive: an order with no lines still reports what its operations produced.
            return calculateCompletedQuantity(operations, materials, workOrder.getPlannedQuantity());
        }

        BigDecimal total = BigDecimal.ZERO;
        for (WorkOrderLine line : lines) {
            List<WorkOrderMaterial> lineMaterials = materialsOfLine(materials, line);
            List<WorkOrderOperation> lineOperations = operationsOfLine(operations, line);

            BigDecimal lineCompleted =
                    calculateLineCompletedQuantity(line, lineOperations, lineMaterials);
            line.setCompletedQuantity(lineCompleted);
            refreshLineStatus(line, lineOperations);
            workOrderLineRepository.save(line);
            total = total.add(lineCompleted);
        }
        return total;
    }

    /** States decided administratively; what a line's operations are doing does not override them. */
    private static final Set<WorkOrderStatus> ADMINISTRATIVE_LINE_STATUSES = EnumSet.of(
            WorkOrderStatus.CANCELLED,
            WorkOrderStatus.CLOSED,
            WorkOrderStatus.SHORT_CLOSED,
            WorkOrderStatus.HOLD);

    /**
     * Advances a line's status to match its own operations.
     *
     * <p>A line's status was only ever written twice: CREATED when the work order was built, and
     * COMPLETED when the whole order was completed. A line whose operations had all finished
     * therefore still read CREATED — on a multi-line order, one finished item was
     * indistinguishable from one not yet started.
     *
     * <p>Left alone when the line has no operations to judge by, or when it sits in a state that
     * was chosen administratively rather than earned by production.
     */
    private void refreshLineStatus(WorkOrderLine line, List<WorkOrderOperation> lineOperations) {
        if (lineOperations.isEmpty() || ADMINISTRATIVE_LINE_STATUSES.contains(line.getStatus())) {
            return;
        }

        boolean anyCompleted = lineOperations.stream()
                .anyMatch(op -> op.getStatus() == OperationStatus.COMPLETED);
        boolean anyStarted = lineOperations.stream()
                .anyMatch(op -> op.getStatus() == OperationStatus.IN_PROGRESS
                        || op.getStatus() == OperationStatus.COMPLETED);
        // Cancelled operations count as settled so a line is not held open by one that will
        // never run, but a line made up entirely of them has produced nothing and is not
        // complete — hence the anyCompleted requirement.
        boolean allSettled = lineOperations.stream()
                .allMatch(op -> op.getStatus() == OperationStatus.COMPLETED
                        || op.getStatus() == OperationStatus.CANCELLED);

        if (allSettled && anyCompleted) {
            line.setStatus(WorkOrderStatus.COMPLETED);
        } else if (anyStarted) {
            line.setStatus(WorkOrderStatus.IN_PROGRESS);
        }
    }

    /**
     * Completion quantity for a single {@link WorkOrderLine}, using that line's own operations,
     * materials and planned quantity. Identical rule to the work-order-level calculation — on a
     * single-line work order the two agree exactly.
     */
    private BigDecimal calculateLineCompletedQuantity(WorkOrderLine line,
                                                      List<WorkOrderOperation> lineOperations,
                                                      List<WorkOrderMaterial> lineMaterials) {
        return calculateCompletedQuantity(lineOperations, lineMaterials, line.getPlannedQuantity());
    }

    /**
     * Shared completion rule. Extracted so a work order and an individual line can be evaluated
     * by exactly the same logic; the only inputs are the relevant operations, materials and
     * planned quantity.
     */
    private BigDecimal calculateCompletedQuantity(List<WorkOrderOperation> operations,
                                                  List<WorkOrderMaterial> materials,
                                                  BigDecimal plannedQuantity) {

        // ---- OPERATION BASED COMPLETION ----
        // Use the last operation's (highest sequence) completed quantity.
        // Intermediate operations may have lower counts due to scrap/reject; only the final
        // output quantity matters for determining how many finished units the WO produced.
        BigDecimal operationCompletedUnits = operations.stream()
                .filter(op -> op.getDeletedDate() == null)
                .max(Comparator.comparingInt(WorkOrderOperation::getSequence))
                .map(op -> op.getCompletedQuantity() != null ? op.getCompletedQuantity() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);


        // ---- MATERIAL BASED COMPLETION ----
        Optional<BigDecimal> materialCompletedUnits = materials.stream()
                .filter(mat -> mat.getDeletedDate() == null)
                .map(mat -> {

                    if (mat.getNetRequiredQuantity() == null ||
                            mat.getNetRequiredQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                        return null;
                    }

                    BigDecimal plannedQty = plannedQuantity != null
                            ? plannedQuantity
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
        // When the WO has no routing operations, operation-based completion is meaningless
        // (it defaults to 0). Completion is then driven purely by materials, falling back to the
        // planned quantity when there is no material-based signal either. Previously the 0 was
        // folded into the min() below, forcing totalCompleted to 0 for operation-less work orders
        // — so finished goods were never produced on completion.
        boolean hasOperations = operations.stream().anyMatch(op -> op.getDeletedDate() == null);
        if (!hasOperations) {
            BigDecimal plannedQty = plannedQuantity != null
                    ? plannedQuantity
                    : BigDecimal.ZERO;
            return materialCompletedUnits.orElse(plannedQty);
        }

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

    public WorkOrderSummaryDTO getWorkOrderSummary(LocalDate today) {
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
                    && status != WorkOrderStatus.CANCELLED
                    && status != WorkOrderStatus.SHORT_CLOSED;

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
    public WorkOrderDTO  updateWorkOrder(int workOrderId, WorkOrderRequestDTO dto) {

        logger.debug("Updating WorkOrder id={}", workOrderId);

        // Fetch existing Work Order
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard — allow update in CREATED or SCHEDULED
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED
                && workOrder.getWorkOrderStatus() != WorkOrderStatus.SCHEDULED) {
            logger.warn(
                    "Update rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "WorkOrder can only be updated in CREATED or SCHEDULED status"
            );
        }

        // Forbidden changes. The BOM and routing of an existing work order are fixed, so a flat
        // bomId/routingId is only ever an echo of what is already there — it is accepted when it
        // names one of the work order's lines and rejected otherwise. Matching against any line
        // (rather than the single editable one) lets a multi-line work order update its header
        // fields without the client having to strip identifiers it just read back.
        List<WorkOrderLine> activeLines = workOrder.activeLines();

        if (dto.getBomId() != null && activeLines.stream()
                .noneMatch(line -> line.getBom() != null && line.getBom().getId() == dto.getBomId())) {
            logger.error("Attempt to change BOM for WorkOrder {}", workOrder.getWorkOrderNumber());
            throw new IllegalStateException("BOM cannot be changed after WorkOrder creation");
        }

        if (dto.getRoutingId() != null && activeLines.stream()
                .noneMatch(line -> line.getRouting() != null
                        && dto.getRoutingId().equals(line.getRouting().getId()))) {
            logger.error("Attempt to change Routing for WorkOrder {}", workOrder.getWorkOrderNumber());
            throw new IllegalStateException("Routing cannot be changed after WorkOrder creation");
        }

        if (dto.getWorkCenterId() != null) {
            WorkCenter workCenter = workCenterRepository.findById(dto.getWorkCenterId())
                    .orElseThrow(() -> new EntityNotFoundException("WorkCenter not found with ID: " + dto.getWorkCenterId()));
            workOrder.setWorkCenter(workCenter);
        }

        // Update allowed fields
        workOrder.setRemarks(dto.getRemarks());
        workOrder.setAllowBackflush(dto.isAllowBackflush());
        workOrder.setDueDate(dto.getDueDate());
        workOrder.setPlannedStartDate(dto.getPlannedStartDate());
        workOrder.setPlannedEndDate(dto.getPlannedEndDate());
        workOrder.setActualStartDate(dto.getActualStartDate());
        workOrder.setActualEndDate(dto.getActualEndDate());

        // Update priority
        if (dto.getPriority() != null) {
            workOrder.setPriority(dto.getPriority());
        }

        // Capture old quantity for audit trail
        BigDecimal oldQty = workOrder.getPlannedQuantity();
        BigDecimal newQty = oldQty;

        BigDecimal oldScrappedQty = workOrder.getScrappedQuantity();
        BigDecimal newScrappedQty = oldScrappedQty;

        // A client that loaded the work order echoes its planned quantity back untouched; that is
        // not an edit, and rescaling by a ratio of one only burns cycles. Engage the rescale — and
        // the single-line requirement it depends on — only when the number actually differs.
        boolean plannedQuantityChanged = dto.getPlannedQuantity() != null
                && dto.getPlannedQuantity().compareTo(BigDecimal.ZERO) > 0
                && dto.getPlannedQuantity().compareTo(oldQty) != 0;

        if (plannedQuantityChanged) {

            // Spreading one flat quantity across several lines is ambiguous — that edit has to
            // name the line it means.
            singleEditableLine(workOrder);

            // ERP Rule: Prevent quantity changes if operations/materials already in progress
            List<WorkOrderOperation> operations =
                    workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());

            if (!operations.isEmpty() && workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED
                    && workOrder.getWorkOrderStatus() != WorkOrderStatus.SCHEDULED) {
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
                        .setScale(5, RoundingMode.HALF_UP);

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
        }

        // Independent of the planned quantity: a request that omits the scrapped quantity leaves
        // the recorded one alone rather than nulling it.
        if (dto.getScrappedQuantity() != null) {
            newScrappedQty = dto.getScrappedQuantity();
            workOrder.setScrappedQuantity(newScrappedQty);
        }

        // ── Source type handling (ID-based) ─────────────────────────────────────
        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.CREATED
                || workOrder.getWorkOrderStatus() == WorkOrderStatus.SCHEDULED) {
            workOrder.setSourceType(dto.getSourceType());
            if (dto.getSourceType() != null) {

                if (dto.getSourceType() == WorkOrderSourceType.SALES_ORDER) {
                    if (dto.getSalesOrderId() == null) {
                        throw new IllegalStateException(
                                "Cannot set source type to SALES_ORDER when no Sales Order ID is provided"
                        );
                    }
                    SalesOrder salesOrder = salesOrderRepository.findById(dto.getSalesOrderId().longValue())
                            .orElseThrow(() -> new EntityNotFoundException("SalesOrder not found"));
                    workOrder.setSalesOrder(salesOrder);
                    workOrder.setParentWorkOrder(null);
                } else if (dto.getSourceType() == WorkOrderSourceType.PARENT_WORK_ORDER) {
                    if (dto.getParentWorkOrderId() == null) {
                        throw new IllegalStateException(
                                "Cannot set source type to PARENT_WORK_ORDER when no Parent Work Order ID is provided"
                        );
                    }
                    WorkOrder parentWO = workOrderRepository.findById(dto.getParentWorkOrderId())
                            .orElseThrow(() -> new EntityNotFoundException("Parent WorkOrder not found"));
                    workOrder.setParentWorkOrder(parentWO);
                    workOrder.setSalesOrder(null);
                } else {
                    workOrder.setSalesOrder(null);
                    workOrder.setParentWorkOrder(null);
                }

                // Only a MANUAL order carries a typed reference. Switching away from MANUAL drops
                // it, so a stale job-card number cannot sit beside a real sales order link.
                workOrder.setReferenceDocument(
                        dto.getSourceType() == WorkOrderSourceType.MANUAL
                                ? dto.getReferenceDocument() : null);
            }
        }

        //  Persist
        WorkOrder updated = workOrderRepository.save(workOrder);

        // Record audit with correct old and new values
        if (!Objects.equals(oldQty, newQty)) {
            auditService.record(
                    workOrder,
                    WorkOrderEventType.UPDATED,
                    "plannedQuantity",
                    oldQty.toString(),
                    newQty.toString(),
                    "Planned quantity updated from " + oldQty + " to " + newQty
            );
        }
        if (!Objects.equals(oldScrappedQty, newScrappedQty)) {
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

        return workOrderMapper.toDTO(updated);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parallel operation helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves operation dependencies from the routing and sets the initial status
     * of each WorkOrderOperation when a work order is released.
     *
     * <p>New mode (routing has explicit RoutingOperationDependency entries):
     * Each operation's dependsOnOperationIds is populated from the routing.
     * Operations with no dependencies → READY.
     * Operations with unresolved dependencies → WAITING_FOR_DEPENDENCY.
     *
     * <p>Legacy mode (no explicit dependencies defined in routing):
     * Falls back to sequential ordering — each operation depends on the previous
     * one by sequence number. Only the first operation is set to READY.
     *
     * @param operations every operation of the work order, ordered by sequence (ascending)
     */
    private void initializeOperationDependencies(List<WorkOrderOperation> operations) {
        // Check if any routing operation has explicit dependencies defined
        boolean hasExplicitDependencies = operations.stream()
                .anyMatch(op -> op.getRoutingOperation() != null
                        && !op.getRoutingOperation().getDependencies().isEmpty());

        if (hasExplicitDependencies) {
            // Build a lookup: routingOperationId → WorkOrderOperation
            Map<Long, WorkOrderOperation> byRoutingOpId = new HashMap<>();
            for (WorkOrderOperation op : operations) {
                if (op.getRoutingOperation() != null) {
                    byRoutingOpId.put(op.getRoutingOperation().getId(), op);
                }
            }

            // Resolve routing-level dependencies into WO-level operation IDs
            for (WorkOrderOperation op : operations) {
                if (op.getRoutingOperation() == null) continue;

                Set<Long> depIds = new HashSet<>();
                for (RoutingOperationDependency rod
                        : op.getRoutingOperation().getDependencies()) {

                    if (rod.getDependencyType() == DependencyType.SEQUENTIAL) {
                        WorkOrderOperation depWoOp =
                                byRoutingOpId.get(rod.getDependsOnRoutingOperation().getId());
                        if (depWoOp != null) {
                            depIds.add(depWoOp.getId());
                        }
                    }
                }
                op.setDependsOnOperationIds(depIds);
                op.setParallelPath(op.getRoutingOperation().getParallelPath());
            }
        } else {
            // Legacy mode: each op depends on the previous by sequence — within its own line.
            // The lines of a multi-line work order are independent chains, so walking the whole
            // sequence-ordered list would wire line 2's first operation behind line 1's last.
            Map<Long, List<WorkOrderOperation>> operationsByLine = operations.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            op -> op.getWorkOrderLine() != null ? op.getWorkOrderLine().getId() : -1L,
                            LinkedHashMap::new,
                            java.util.stream.Collectors.toList()));

            for (List<WorkOrderOperation> lineOperations : operationsByLine.values()) {
                WorkOrderOperation prev = null;
                for (WorkOrderOperation op : lineOperations) {
                    if (prev != null) {
                        op.setDependsOnOperationIds(new HashSet<>(Set.of(prev.getId())));
                    }
                    prev = op;
                }
            }
        }

        // Set initial statuses based on resolved dependencies
        for (WorkOrderOperation op : operations) {
            if (op.getDependsOnOperationIds().isEmpty()) {
                op.setStatus(OperationStatus.READY);
                // An operation's own planned quantity is the quantity of the line it belongs to.
                // The work order's planned quantity is the total across every line, which would
                // hand each line's first operation the whole work order's worth of input.
                op.setAvailableInputQuantity(op.getPlannedQuantity());
                logger.info("Operation [{} - {}] set to READY (no dependencies)",
                        op.getSequence(), op.getOperationName());
            } else {
                op.setStatus(OperationStatus.WAITING_FOR_DEPENDENCY);
                logger.info("Operation [{} - {}] set to WAITING_FOR_DEPENDENCY ({} deps)",
                        op.getSequence(), op.getOperationName(), op.getDependsOnOperationIds().size());
            }
        }

        workOrderOperationRepository.saveAll(operations);
    }

    /**
     * After an operation is marked COMPLETED, finds all operations that depend on it
     * and unlocks any that now have all their dependencies satisfied.
     *
     * <p>New mode: uses the dependsOnOperationIds set (populated at release time).
     * Unlocked operations get status READY and their availableInputQuantity raised to
     * their own planned quantity — the quantity of the line they belong to.
     *
     * <p>Legacy mode (no dependents found via dependsOnOperationIds):
     * Falls back to unlocking the next-by-sequence operation.
     *
     * @param completed  the operation that just became COMPLETED
     * @param completedQty  the quantity completed (used for legacy sequential forwarding)
     * @param workOrder  parent work order (used for legacy lookup)
     */
    private void unlockEligibleDependents(WorkOrderOperation completed,
                                           BigDecimal completedQty,
                                           WorkOrder workOrder) {
        List<WorkOrderOperation> dependents =
                workOrderOperationRepository.findByDependsOnOperationId(completed.getId());

        if (!dependents.isEmpty()) {
            // New dependency mode
            for (WorkOrderOperation dep : dependents) {
                List<WorkOrderOperation> allDeps =
                        workOrderOperationRepository.findAllById(dep.getDependsOnOperationIds());

                boolean allComplete = allDeps.stream()
                        .allMatch(d -> d.getStatus() == OperationStatus.COMPLETED);

                if (allComplete) {
                    // Never revert an operation that is already past the READY gate
                    if (dep.getStatus() == OperationStatus.COMPLETED
                            || dep.getStatus() == OperationStatus.IN_PROGRESS) {
                        logger.info("Skipping unlock for op [{} - {}] — already {}",
                                dep.getSequence(), dep.getOperationName(), dep.getStatus());
                        continue;
                    }
                    dep.setStatus(OperationStatus.READY);
                    // Every dependency is complete, so the operation can run its full quantity —
                    // its own, which is its line's, not the work order's total across all lines.
                    // Raised rather than assigned so an over-completion that already forwarded
                    // more than the planned quantity is not clipped back.
                    dep.setAvailableInputQuantity(
                            dep.getAvailableInputQuantity().max(dep.getPlannedQuantity()));
                    dep.setDependencyResolvedDate(new Date());
                    workOrderOperationRepository.save(dep);

                    logger.info("Unlocked operation [{} - {}] for WorkOrder {} — all dependencies complete",
                            dep.getSequence(), dep.getOperationName(), workOrder.getWorkOrderNumber());
                }
            }
        } else {
            // Legacy mode: unlock next-by-sequence
            WorkOrderOperation nextOp =
                    workOrderOperationRepository
                            .findTopByWorkOrderLineAndSequenceGreaterThanOrderBySequenceAsc(
                                    completed.getWorkOrderLine(), completed.getSequence()
                            );

            if (nextOp != null) {
                nextOp.setAvailableInputQuantity(nextOp.getAvailableInputQuantity().add(completedQty));
                if (nextOp.getStatus() != OperationStatus.COMPLETED
                        && nextOp.getStatus() != OperationStatus.IN_PROGRESS) {
                    nextOp.setStatus(OperationStatus.READY);
                }
                workOrderOperationRepository.save(nextOp);

                logger.info("Next operation [{} - {}] set to READY with availableInput={} for WorkOrder {}",
                        nextOp.getSequence(), nextOp.getOperationName(),
                        nextOp.getAvailableInputQuantity(), workOrder.getWorkOrderNumber());
            }
        }
    }

    @Transactional
    @Override
    public WorkOrderDTO releaseWorkOrder(int workOrderId, boolean forceRelease) {

        logger.debug("Releasing WorkOrder id={}", workOrderId);

        //  Fetch Work Order
        WorkOrder workOrder = workOrderRepository.findById( workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        //  Status guard — allow release from CREATED, SCHEDULED, or HOLD (re-submit after rejection)
        String previousStatus = workOrder.getWorkOrderStatus().name();
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.CREATED
                && workOrder.getWorkOrderStatus() != WorkOrderStatus.SCHEDULED
                && workOrder.getWorkOrderStatus() != WorkOrderStatus.HOLD) {
            logger.warn(
                    "Release rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "Only WorkOrders in CREATED, SCHEDULED, or HOLD status can be released"
            );
        }

        //  Validate BOM & Routing — every line must have both, and there must be a line at all
        List<WorkOrderLine> releaseLines = workOrder.activeLines();
        if (releaseLines.isEmpty()
                || releaseLines.stream().anyMatch(l -> l.getBom() == null || l.getRouting() == null)) {
            logger.error(
                    "Release failed for WorkOrder {} due to missing BOM/Routing on one or more lines",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException("BOM and Routing are mandatory to release WorkOrder");
        }

        //  Validate materials
        List<WorkOrderMaterial> materials =
                workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());

        List<WorkOrderOperation> operations =
                workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());

        if (materials.isEmpty() && operations.isEmpty()) {
            logger.error(
                    "Release failed for WorkOrder {}: Both materials and operations are missing",
                    workOrder.getWorkOrderNumber()
            );
            throw new IllegalStateException("WorkOrder must have materials or operations before release");
        }

        // Generate one InventoryRequest (Material Request) per material.
        // Stock reservation is deferred to the Stores approval step.
        // forceRelease is no longer used (Stores controls availability gating).
        int mrCount = 0;
        for (WorkOrderMaterial material : materials) {
            if (material.getPlannedRequiredQuantity().compareTo(BigDecimal.ZERO) > 0) {
                com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest mr =
                        new com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest();
                mr.setRequestSource(com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource.WORK_ORDER);
                mr.setSourceId((long)workOrder.getId());
                mr.setInventoryItem(material.getComponent());
                mr.setRequestedQuantity(material.getPlannedRequiredQuantity());
                mr.setApprovalStatus(com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.PENDING);
                mr.setRequestedBy("SYSTEM");
                mr.setRequestedDate(new java.util.Date());
                mr.setReferenceNumber(workOrder.getWorkOrderNumber() + "-MAT-" + material.getId());
                mr = inventoryRequestRepository.save(mr);
                material.setInventoryRequestId(mr.getId());
                workOrderMaterialRepository.save(material);
                mrCount++;
            }
        }

        // If no MRs were generated (no materials or all zero-quantity), skip the Stores approval
        // step and go directly to READY_FOR_PRODUCTION so the WO does not get stuck.
        WorkOrderStatus releaseStatus = mrCount > 0
                ? WorkOrderStatus.MATERIAL_PENDING
                : WorkOrderStatus.READY_FOR_PRODUCTION;
        workOrder.setWorkOrderStatus(releaseStatus);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} released — {} MRs generated → status {}",
                workOrder.getWorkOrderNumber(), mrCount, releaseStatus
        );

        //  Initialize operation statuses with dependency awareness
        if (!operations.isEmpty()) {
            initializeOperationDependencies(operations);
        }
        String auditNote = mrCount > 0
                ? "Material requests generated, awaiting Stores approval"
                : "No materials required — released directly to production";
        auditService.record(
                workOrder,
                WorkOrderEventType.RELEASED,
                "status",
                previousStatus,
                releaseStatus.name(),
                auditNote
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
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.READY_FOR_PRODUCTION &&
                workOrder.getWorkOrderStatus() != WorkOrderStatus.PARTIALLY_READY &&
                workOrder.getWorkOrderStatus() != WorkOrderStatus.IN_PROGRESS) {

            logger.warn(
                    "Cannot start operation {} for WorkOrder {} due to WO status {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );

            throw new IllegalStateException(
                    "Operation can only be started when WorkOrder is READY_FOR_PRODUCTION, PARTIALLY_READY, or IN_PROGRESS"
            );
        }

        // Validate operation status
        if (operation.getStatus() != OperationStatus.READY && 
            operation.getStatus() != OperationStatus.WAITING_FOR_DEPENDENCY) {
            
            logger.warn(
                    "Cannot start operation {} for WorkOrder {} due to operation status {}",
                    operation.getSequence(),
                    workOrder.getWorkOrderNumber(),
                    operation.getStatus()
            );
            throw new IllegalStateException("Only READY or WAITING_FOR_DEPENDENCY operations can be started");
        }

        // Hard gate: upstream input must be ≥ 1. Materials checked at batch completion.
        validateOperationReadiness(operation);

        //  Validate dependencies before starting
        //  New mode: check explicit dependsOnOperationIds (populated at release time).
        //  Legacy mode (empty set): fall back to previous-by-sequence check.
        // Note: Generic dependency check is now handled via availableInputQuantity gate
        // in validateOperationReadiness. We skip the 'all COMPLETED' check to allow partial flow.

        // Material gate: for PARTIALLY_READY WOs, check that materials for this operation
        // (WO-level + operation-specific) have been approved by the Store Keeper (MR approved/partial).
        // For READY_FOR_PRODUCTION and IN_PROGRESS all MRs are already approved — skip the check.
        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.PARTIALLY_READY) {
            List<WorkOrderMaterial> opMaterials = workOrderMaterialRepository
                    .findByWorkOrderAndWorkOrderOperationIsNull(workOrder);
            opMaterials.addAll(workOrderMaterialRepository
                    .findByWorkOrderAndWorkOrderOperation(workOrder, operation));

            List<String> unapproved = opMaterials.stream()
                    .filter(m -> {
                        if (m.getInventoryRequestId() == null) return true;
                        return inventoryRequestRepository.findById(m.getInventoryRequestId())
                                .map(mr -> mr.getApprovalStatus() != com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.APPROVED
                                        && mr.getApprovalStatus() != com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.PARTIAL)
                                .orElse(true);
                    })
                    .map(m -> m.getComponent().getItemCode())
                    .collect(java.util.stream.Collectors.toList());

            if (!unapproved.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot start operation — Store Keeper has not yet approved materials: "
                        + String.join(", ", unapproved)
                );
            }
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

        // The line this operation belongs to is under way too — without this it would stay on its
        // creation status until the first batch was submitted.
        WorkOrderLine startedLine = operation.getWorkOrderLine();
        if (startedLine != null) {
            List<WorkOrderOperation> lineOperations =
                    workOrderOperationRepository.findByWorkOrder(workOrder).stream()
                            .filter(o -> o.getDeletedDate() == null)
                            .filter(o -> belongsToLine(o.getWorkOrderLine(), startedLine))
                            .toList();
            refreshLineStatus(startedLine, lineOperations);
            workOrderLineRepository.save(startedLine);
        }

        // Update WorkOrder status to IN_PROGRESS on first operation start
        WorkOrderStatus currentStatus = workOrder.getWorkOrderStatus();
        if (currentStatus == WorkOrderStatus.READY_FOR_PRODUCTION
                || currentStatus == WorkOrderStatus.PARTIALLY_READY
                || currentStatus == WorkOrderStatus.RELEASED
                || currentStatus == WorkOrderStatus.SCHEDULED) {
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
        WorkOrderStatus wos = workOrder.getWorkOrderStatus();
        if (wos != WorkOrderStatus.READY_FOR_PRODUCTION
                && wos != WorkOrderStatus.PARTIALLY_READY
                && wos != WorkOrderStatus.IN_PROGRESS) {

            throw new IllegalStateException(
                    "Materials can only be issued when WorkOrder is READY_FOR_PRODUCTION, PARTIALLY_READY, or IN_PROGRESS"
            );
        }

        for (IssueWorkOrderMaterialDTO.MaterialIssueItem item : issueDTO.getMaterials()) {

            WorkOrderMaterial material = workOrderMaterialRepository
                    .findByIdWithComponent(item.getWorkOrderMaterialId())
                    .orElseThrow(() -> new EntityNotFoundException("WorkOrderMaterial not found"));

            // Validate material belongs to work order
            if (material.getWorkOrder().getId() != workOrder.getId()) {
                logger.error("Material ownership mismatch: Material ID {} belongs to WO #{}, but issue request is for WO #{}", 
                    material.getId(), material.getWorkOrder().getWorkOrderNumber(), workOrder.getWorkOrderNumber());
                throw new IllegalStateException("Internal consistency error: Material does not belong to the current Work Order.");
            }

            // ----- OPERATION GATE -----
            // If this material is linked to a specific operation, that operation
            // must be READY or IN_PROGRESS before issue is allowed.
            if (material.getWorkOrderOperation() != null) {
                OperationStatus opStatus = material.getWorkOrderOperation().getStatus();
                if (opStatus != OperationStatus.READY
                        && opStatus != OperationStatus.IN_PROGRESS
                        && opStatus != OperationStatus.COMPLETED) {
                    throw new IllegalStateException(
                            "Material '" + material.getComponent().getItemCode() +
                            "' can only be issued when operation '" +
                            material.getWorkOrderOperation().getOperationName() +
                            "' is READY, IN_PROGRESS, or COMPLETED (current: " + opStatus + ")"
                    );
                }
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

            if (newIssued.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Issued quantity cannot be negative");
            }

            if (newScrap.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Scrap quantity cannot be negative");
            }

            if (newIssued.compareTo(BigDecimal.ZERO) == 0 && newScrap.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("At least one of issued quantity or scrap quantity must be greater than zero");
            }

            // ----- CALCULATIONS -----

            BigDecimal totalIssued = currentIssued.add(newIssued);
            BigDecimal totalScrapped = currentScrapped.add(newScrap);

            // Prevent over-issue beyond effective cap (planned + approved reorder quantities)
            if (newIssued.compareTo(BigDecimal.ZERO) > 0 &&
                    totalIssued.compareTo(effectiveIssueCap(material)) > 0) {
                throw new IllegalStateException(
                        "Issued quantity exceeds planned + approved reorder quantity"
                );
            }

            // Scrap cannot exceed total ever issued (including this call)
            if (totalScrapped.compareTo(totalIssued) > 0) {
                throw new IllegalStateException(
                        "Scrap quantity cannot exceed total issued quantity (" + totalIssued + ")"
                );
            }

            // Update quantities
            material.setIssuedQuantity(totalIssued);
            material.setScrappedQuantity(totalScrapped);

            // Record physical floor movement only when new stock is actually being issued
            if (newIssued.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    InventoryTransactionDTO issueDto = new InventoryTransactionDTO();
                    issueDto.setInventoryItemId(material.getComponent().getInventoryItemId());
                    issueDto.setQuantity(newIssued.doubleValue());
                    issueDto.setScrappedQuantity(newScrap.doubleValue());
                    issueDto.setTransactionType("ISSUE");
                    issueDto.setReferenceType("WORK_ORDER");
                    issueDto.setReferenceDocNo(workOrder.getWorkOrderNumber());
                    if (item.getOverrideInstanceIds() != null && !item.getOverrideInstanceIds().isEmpty()) {
                        issueDto.setOverrideInstanceIds(item.getOverrideInstanceIds());
                        issueDto.setOverrideReason(item.getOverrideReason());
                    }
                    inventoryTransactionService.issueStock(issueDto);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to record material issue: " + e.getMessage(), e);
                }
            }

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

        BigDecimal totalCompleted = refreshCompletedQuantities(workOrder);
        workOrder.setCompletedQuantity(totalCompleted);

        workOrderRepository.save(workOrder);
    }


    @Transactional
    @Override
    public List<String> completeOperationPartial(PartialOperationCompleteDTO partialCompleteDTO) {

        List<String> warnings = new ArrayList<>();

        BigDecimal batchQty = partialCompleteDTO.getCompletedQuantity() != null
                ? partialCompleteDTO.getCompletedQuantity() : BigDecimal.ZERO;
        BigDecimal rejectedQty = partialCompleteDTO.getRejectedQuantity() != null
                ? partialCompleteDTO.getRejectedQuantity() : BigDecimal.ZERO;
        BigDecimal scrapQty = partialCompleteDTO.getScrappedQuantity() != null
                ? partialCompleteDTO.getScrappedQuantity() : BigDecimal.ZERO;
        // ─── Quantity validation ────────────────────────────────────────────────
        if (batchQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Completed quantity cannot be negative");
        }
        if (rejectedQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Rejected quantity cannot be negative");
        }
        if (scrapQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Scrap quantity cannot be negative");
        }
        if (batchQty.compareTo(BigDecimal.ZERO) == 0
                && rejectedQty.compareTo(BigDecimal.ZERO) == 0
                && scrapQty.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException(
                    "At least one of completedQuantity, scrappedQuantity, or rejectedQuantity must be greater than zero");
        }

        logger.info("Completing operation id={} good={} rejected={} scrap={}",
                partialCompleteDTO.getOperationId(), batchQty, rejectedQty, scrapQty);

        // ─── Fetch operation ────────────────────────────────────────────────────
        WorkOrderOperation operation = workOrderOperationRepository
                .findById(partialCompleteDTO.getOperationId())
                .orElseThrow(() -> {
                    logger.error("Operation not found id={}", partialCompleteDTO.getOperationId());
                    return new EntityNotFoundException("Operation not found");
                });

        WorkOrder workOrder = operation.getWorkOrder();

        // ─── Work Order status guard ────────────────────────────────────────────
        WorkOrderStatus woStatus = workOrder.getWorkOrderStatus();
        if (woStatus == WorkOrderStatus.COMPLETED
                || woStatus == WorkOrderStatus.CLOSED
                || woStatus == WorkOrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot record operation completion: Work Order " +
                    workOrder.getWorkOrderNumber() + " is " + woStatus);
        }

        // ─── Status check ───────────────────────────────────────────────────────
        if (operation.getStatus() != OperationStatus.IN_PROGRESS &&
                operation.getStatus() != OperationStatus.READY &&
                operation.getStatus() != OperationStatus.COMPLETED) {
            throw new IllegalStateException("Only READY, IN_PROGRESS, or COMPLETED operations can be updated");
        }

        // ─── Reason code validation ─────────────────────────────────────────────
        if (rejectedQty.compareTo(BigDecimal.ZERO) > 0 &&
                (partialCompleteDTO.getRejectionReasonCode() == null ||
                 partialCompleteDTO.getRejectionReasonCode().isBlank())) {
            throw new IllegalArgumentException("Rejection reason code is required when rejected quantity > 0");
        }
        if (scrapQty.compareTo(BigDecimal.ZERO) > 0 &&
                (partialCompleteDTO.getScrapReasonCode() == null ||
                 partialCompleteDTO.getScrapReasonCode().isBlank())) {
            throw new IllegalArgumentException("Scrap reason code is required when scrap quantity > 0");
        }

        BigDecimal currentCompleted = operation.getCompletedQuantity();
        BigDecimal newCompleted = currentCompleted.add(batchQty);
        boolean wasAlreadyCompleted = operation.getStatus() == OperationStatus.COMPLETED;

        // ─── GATE 1: Input Qty Gate — good output cannot exceed forwarded input ──
        // Scrap and reject are quality outcomes of units already in the operation;
        // they reduce yield but do not consume additional input capacity.
        BigDecimal effectiveInput = operation.getAvailableInputQuantity()
                .add(extraInputFromReorders(operation, workOrder));

        if (newCompleted.compareTo(effectiveInput) > 0) {
            BigDecimal remaining = effectiveInput.subtract(currentCompleted).max(BigDecimal.ZERO);
            throw new IllegalStateException(
                    "Input gate: Only " + remaining + " good units can still be completed. " +
                    "Effective input: " + effectiveInput +
                    ", already completed: " + currentCompleted);
        }

        BigDecimal batchTotal = batchQty.add(rejectedQty).add(scrapQty);

        // ─── Over-completion: hard-block if any rejections are pending disposition ───
        // The user must accept/scrap/rework all pending rejections first — those decisions
        // determine whether over-completion is even necessary (e.g., ACCEPT recovers the qty).
        if (newCompleted.compareTo(workOrder.getPlannedQuantity()) > 0) {
            List<RejectionEntry> pendingRejections = rejectionEntryRepository
                    .findByWorkOrderIdAndDispositionStatus(workOrder.getId(), DispositionStatus.PENDING);
            if (!pendingRejections.isEmpty()) {
                BigDecimal pendingQty = pendingRejections.stream()
                        .map(RejectionEntry::getRejectedQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                throw new IllegalStateException(
                        "Over-completion blocked — " + pendingRejections.size() +
                        " rejection(s) totaling " + pendingQty +
                        " unit(s) await disposition. Accept, scrap, or rework them from " +
                        "Yield & Rejection before recording extra units.");
            }

            // Soft warning — over-completion allowed once rejections are settled
            String warn = "Exceeds WO quantity — additional material may be needed. " +
                    "Completed: " + newCompleted + ", WO planned: " + workOrder.getPlannedQuantity();
            warnings.add(warn);
            logger.warn("WO {} op '{}': {}", workOrder.getWorkOrderNumber(), operation.getOperationName(), warn);
        }

        // ─── GATE 2: Consume materials for all processed units ─────────────────
        // Good + rejected + scrap all consume input materials at operation time.
        consumeMaterialsForBatch(operation, workOrder, batchTotal);

        // For scrap units, additionally record scrap on material records
        if (scrapQty.compareTo(BigDecimal.ZERO) > 0) {
            recordScrapOnMaterials(operation, workOrder, scrapQty);
        }

        // ─── Update Operation quantities ────────────────────────────────────────
        operation.setCompletedQuantity(newCompleted);
        operation.setScrappedQuantity(operation.getScrappedQuantity().add(scrapQty));
        operation.setRejectedQuantity(operation.getRejectedQuantity().add(rejectedQty));

        if (partialCompleteDTO.getRejectionReasonCode() != null) {
            operation.setRejectionReasonCode(partialCompleteDTO.getRejectionReasonCode());
        }
        if (partialCompleteDTO.getScrapReasonCode() != null) {
            operation.setScrapReasonCode(partialCompleteDTO.getScrapReasonCode());
        }

        operation.setStatus(OperationStatus.IN_PROGRESS);
        if (operation.getActualStartDate() == null) {
            operation.setActualStartDate(new Date());
        }

        // ─── Create RejectionEntry for units pending MRB disposition ───────────
        if (rejectedQty.compareTo(BigDecimal.ZERO) > 0) {
            RejectionEntry rejection = new RejectionEntry();
            rejection.setWorkOrder(workOrder);
            rejection.setOperation(operation);
            rejection.setRejectedQuantity(rejectedQty);
            rejection.setDispositionStatus(DispositionStatus.PENDING);
            try {
                rejection.setCreatedBy(
                    SecurityContextHolder.getContext().getAuthentication().getName());
            } catch (Exception ignored) {
                rejection.setCreatedBy("system");
            }
            rejectionEntryRepository.save(rejection);
            logger.info("RejectionEntry created: {} units pending disposition on op '{}' WO {}",
                    rejectedQty, operation.getOperationName(), workOrder.getWorkOrderNumber());
        }

        // ─── Mark as COMPLETED or forward partial qty ───────────────────────────
        if (wasAlreadyCompleted) {
            // Over-completion on an already-COMPLETED op: push extra batch forward and restore COMPLETED.
            // Must NOT call unlockEligibleDependents again — that would double-add the full qty.
            forwardPartialQuantityToDependents(operation, batchQty, workOrder);
            operation.setStatus(OperationStatus.COMPLETED); // line 1499 set it IN_PROGRESS; restore it
            logger.info("Op [{} - {}] over-completion: extra {} forwarded to dependents for WO {}",
                    operation.getSequence(), operation.getOperationName(),
                    batchQty, workOrder.getWorkOrderNumber());
        } else if (newCompleted.compareTo(operation.getPlannedQuantity()) >= 0) {
            operation.setStatus(OperationStatus.COMPLETED);
            operation.setActualEndDate(new Date());
            unlockEligibleDependents(operation, newCompleted, workOrder);
            logger.info("Operation [{} - {}] fully COMPLETED for WorkOrder {}",
                    operation.getSequence(), operation.getOperationName(),
                    workOrder.getWorkOrderNumber());
        } else {
            // Only good units flow forward to downstream operations
            forwardPartialQuantityToDependents(operation, batchQty, workOrder);
            logger.info("Operation [{} - {}] partially completed ({}/{}) for WorkOrder {}",
                    operation.getSequence(), operation.getOperationName(),
                    newCompleted, operation.getPlannedQuantity(),
                    workOrder.getWorkOrderNumber());
        }

        workOrderOperationRepository.save(operation);

        // ─── Update WorkOrder aggregate quantities ──────────────────────────────
        BigDecimal totalCompleted = refreshCompletedQuantities(workOrder);
        workOrder.setCompletedQuantity(totalCompleted);

        WorkOrderStatus batchWoStatus = workOrder.getWorkOrderStatus();
        if (batchWoStatus == WorkOrderStatus.READY_FOR_PRODUCTION
                || batchWoStatus == WorkOrderStatus.PARTIALLY_READY
                || batchWoStatus == WorkOrderStatus.RELEASED
                || batchWoStatus == WorkOrderStatus.SCHEDULED) {
            workOrder.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
            workOrder.setActualStartDate(new Date());
        }

        workOrderRepository.save(workOrder);

        // ─── Audit ──────────────────────────────────────────────────────────────
        String detail = "good=" + batchQty + ", rejected=" + rejectedQty + ", scrap=" + scrapQty;
        if (partialCompleteDTO.getRejectionReasonCode() != null)
            detail += ", rejectionCode=" + partialCompleteDTO.getRejectionReasonCode();
        if (partialCompleteDTO.getScrapReasonCode() != null)
            detail += ", scrapCode=" + partialCompleteDTO.getScrapReasonCode();
        if (partialCompleteDTO.getRemarks() != null)
            detail += ". " + partialCompleteDTO.getRemarks();

        auditService.record(
                workOrder,
                WorkOrderEventType.OPERATION_COMPLETED,
                "operation",
                currentCompleted.toString(),
                newCompleted.toString(),
                detail
        );

        return warnings;
    }

    /**
     * Marks scrap material quantity on WorkOrderMaterial records.
     * Called after consumeMaterialsForBatch so consumption is already recorded.
     */
    private void recordScrapOnMaterials(WorkOrderOperation operation, WorkOrder workOrder, BigDecimal scrapQty) {
        List<WorkOrderMaterial> opMaterials =
                workOrderMaterialRepository.findByWorkOrderOperationId(operation.getId());

        boolean isFirstOperation = workOrderOperationRepository
                .findTopByWorkOrderLineAndSequenceLessThanOrderBySequenceDesc(
                        operation.getWorkOrderLine(), operation.getSequence()) == null;
        if (isFirstOperation) {
            opMaterials = new ArrayList<>(opMaterials);
            opMaterials.addAll(workOrderMaterialRepository.findByWorkOrderAndWorkOrderOperationIsNull(workOrder));
        }

        BigDecimal woPlannedQty = workOrder.getPlannedQuantity();
        if (woPlannedQty.compareTo(BigDecimal.ZERO) <= 0) woPlannedQty = BigDecimal.ONE;

        for (WorkOrderMaterial material : opMaterials) {
            if (material.getDeletedDate() != null) continue;
            BigDecimal reqPerUnit = material.getNetRequiredQuantity()
                    .divide(woPlannedQty, 10, RoundingMode.HALF_UP);
            BigDecimal scrapMaterialQty = scrapQty.multiply(reqPerUnit).setScale(5, RoundingMode.HALF_UP);
            if (scrapMaterialQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal existing = material.getScrappedQuantity() != null
                    ? material.getScrappedQuantity() : BigDecimal.ZERO;
            material.setScrappedQuantity(existing.add(scrapMaterialQty));
            workOrderMaterialRepository.save(material);
        }
    }

    /**
     * Hard gate: upstream input must be ≥ 1 to start. No material check — materials are
     * enforced at batch completion time (completeOperationPartial).
     */
    private void validateOperationReadiness(WorkOrderOperation op) {
        if (op.getAvailableInputQuantity().compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalStateException(
                    "Operation '" + op.getOperationName() + "' cannot start: " +
                    "no output forwarded from upstream dependencies yet " +
                    "(availableInput=" + op.getAvailableInputQuantity() + ")"
            );
        }
    }

    /**
     * Consumes materials proportional to batchQty at partial-completion time.
     * Hard gate if allowBackflush is false and floor qty is insufficient.
     */
    private void consumeMaterialsForBatch(WorkOrderOperation operation, WorkOrder workOrder, BigDecimal batchQty) {
        List<WorkOrderMaterial> opMaterials =
                workOrderMaterialRepository.findByWorkOrderOperationId(operation.getId());

        boolean isFirstOperation = workOrderOperationRepository
                .findTopByWorkOrderLineAndSequenceLessThanOrderBySequenceDesc(
                        operation.getWorkOrderLine(), operation.getSequence()) == null;

        if (isFirstOperation) {
            opMaterials = new ArrayList<>(opMaterials);
            opMaterials.addAll(workOrderMaterialRepository.findByWorkOrderAndWorkOrderOperationIsNull(workOrder));
        }

        BigDecimal woPlannedQty = workOrder.getPlannedQuantity();
        if (woPlannedQty.compareTo(BigDecimal.ZERO) <= 0) woPlannedQty = BigDecimal.ONE;

        for (WorkOrderMaterial material : opMaterials) {
            if (material.getDeletedDate() != null) continue;

            BigDecimal reqPerUnit = material.getNetRequiredQuantity()
                    .divide(woPlannedQty, 10, RoundingMode.HALF_UP);
            BigDecimal consumeQty = batchQty.multiply(reqPerUnit).setScale(5, RoundingMode.HALF_UP);

            if (consumeQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal issued = material.getIssuedQuantity() != null ? material.getIssuedQuantity() : BigDecimal.ZERO;
            BigDecimal consumed = material.getConsumedQuantity() != null ? material.getConsumedQuantity() : BigDecimal.ZERO;
            BigDecimal availableOnFloor = issued.subtract(consumed);

            if (consumeQty.compareTo(availableOnFloor) > 0) {
                if (!workOrder.isAllowBackflush()) {
                    throw new IllegalStateException("Insufficient issued material for '" +
                            material.getComponent().getItemCode() + "'. Needed: " +
                            consumeQty.setScale(2, RoundingMode.HALF_UP) + ", On Floor: " +
                            availableOnFloor.setScale(2, RoundingMode.HALF_UP) +
                            ". Please issue materials first.");
                }
                BigDecimal deficit = consumeQty.subtract(availableOnFloor);
                BigDecimal newIssued = issued.add(deficit);

                // Backflush still respects the Stores approval gate — auto-issued total
                // cannot exceed what was actually approved (original MR + reorder MRs).
                BigDecimal approvedCap = approvedIssueCap(material);
                if (newIssued.compareTo(approvedCap) > 0) {
                    throw new IllegalStateException("Backflush blocked for '" +
                            material.getComponent().getItemCode() + "'. Approved: " +
                            approvedCap.setScale(2, RoundingMode.HALF_UP) + ", needed: " +
                            newIssued.setScale(2, RoundingMode.HALF_UP) +
                            ". Get the Material Request approved before recording this batch.");
                }

                material.setIssuedQuantity(newIssued);
                logger.info("Backflush: auto-issued {} of '{}' (within approved cap {})",
                        deficit, material.getComponent().getItemCode(), approvedCap);
            }

            try {
                InventoryTransactionDTO consumeDto = new InventoryTransactionDTO();
                consumeDto.setInventoryItemId(material.getComponent().getInventoryItemId());
                consumeDto.setQuantity(consumeQty.doubleValue());
                consumeDto.setTransactionType("CONSUME");
                consumeDto.setReferenceType("WORK_ORDER");
                consumeDto.setReferenceDocNo(workOrder.getWorkOrderNumber());
                inventoryTransactionService.consumeStock(consumeDto);
            } catch (Exception e) {
                throw new IllegalStateException("Consumption failed for '" +
                        material.getComponent().getItemCode() + "': " + e.getMessage(), e);
            }

            material.setConsumedQuantity(consumed.add(consumeQty));
            workOrderMaterialRepository.save(material);

            logger.info("Consumed {} of '{}' for batch on operation '{}' (total consumed: {})",
                    consumeQty, material.getComponent().getItemCode(),
                    operation.getOperationName(), material.getConsumedQuantity());
        }
    }

    /**
     * Pushes partially completed quantity to all dependent operations.
     */
    private void forwardPartialQuantityToDependents(WorkOrderOperation completed,
                                                   BigDecimal batchQty,
                                                   WorkOrder workOrder) {
        List<WorkOrderOperation> dependents =
                workOrderOperationRepository.findByDependsOnOperationId(completed.getId());

        if (!dependents.isEmpty()) {
            for (WorkOrderOperation dep : dependents) {
                dep.setAvailableInputQuantity(dep.getAvailableInputQuantity().add(batchQty));

                if (dep.getStatus() == OperationStatus.WAITING_FOR_DEPENDENCY &&
                        dep.getAvailableInputQuantity().compareTo(BigDecimal.ONE) >= 0) {
                    dep.setStatus(OperationStatus.READY);
                    logger.info("Op '{}' on WO {} → READY (input from '{}')",
                            dep.getOperationName(), workOrder.getWorkOrderNumber(), completed.getOperationName());
                }
                workOrderOperationRepository.save(dep);
            }
        } else {
            // Legacy sequential fallback
            WorkOrderOperation nextOp = workOrderOperationRepository
                    .findTopByWorkOrderLineAndSequenceGreaterThanOrderBySequenceAsc(
                            completed.getWorkOrderLine(), completed.getSequence());
            if (nextOp != null) {
                nextOp.setAvailableInputQuantity(nextOp.getAvailableInputQuantity().add(batchQty));
                if (nextOp.getStatus() == OperationStatus.WAITING_FOR_DEPENDENCY &&
                        nextOp.getAvailableInputQuantity().compareTo(BigDecimal.ONE) >= 0) {
                    nextOp.setStatus(OperationStatus.READY);
                    logger.info("Next op '{}' on WO {} → READY", nextOp.getOperationName(), workOrder.getWorkOrderNumber());
                }
                workOrderOperationRepository.save(nextOp);
            }
        }
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

        // Unlock downstream operations
        unlockEligibleDependents(operation, completedQty, workOrder);

        // Update WorkOrder progress
        BigDecimal totalCompleted = refreshCompletedQuantities(workOrder);

        workOrder.setCompletedQuantity(totalCompleted);
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} progress updated: completedQty={}",
                workOrder.getWorkOrderNumber(),
                totalCompleted
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.OPERATION_COMPLETED,
                "operation",
                null,
                operation.getSequence() + " - " + operation.getOperationName(),
                "Operation completed with qty: " + completedQty
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

        //  Status guard — also allow READY_FOR_PRODUCTION when the WO has no operations
        if (workOrder.getWorkOrderStatus() != WorkOrderStatus.IN_PROGRESS
                && workOrder.getWorkOrderStatus() != WorkOrderStatus.READY_FOR_PRODUCTION) {
            logger.warn(
                    "Cannot complete WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    workOrder.getWorkOrderStatus()
            );
            throw new IllegalStateException(
                    "Only IN_PROGRESS or READY_FOR_PRODUCTION WorkOrders can be completed"
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

        // Handle Backflushing
        if (workOrder.isAllowBackflush()) {
            IssueWorkOrderMaterialDTO autoIssueDTO = new IssueWorkOrderMaterialDTO();
            autoIssueDTO.setWorkOrderId(workOrder.getId());
            List<IssueWorkOrderMaterialDTO.MaterialIssueItem> issueItems = new ArrayList<>();

            for (WorkOrderMaterial material : workOrderMaterialRepository.findByWorkOrderId(workOrder.getId())) {
                if (material.getDeletedDate() != null) continue;

                BigDecimal issued = material.getIssuedQuantity() != null ? material.getIssuedQuantity() : BigDecimal.ZERO;
                BigDecimal scrapped = material.getScrappedQuantity() != null ? material.getScrappedQuantity() : BigDecimal.ZERO;
                BigDecimal goodIssued = issued.subtract(scrapped);

                if (goodIssued.compareTo(material.getNetRequiredQuantity()) < 0) {
                    BigDecimal gap = material.getNetRequiredQuantity().subtract(goodIssued);
                    IssueWorkOrderMaterialDTO.MaterialIssueItem item = new IssueWorkOrderMaterialDTO.MaterialIssueItem();
                    item.setWorkOrderMaterialId(material.getId());
                    item.setIssuedQuantity(gap);
                    item.setScrappedQuantity(BigDecimal.ZERO);
                    issueItems.add(item);
                }
            }
            if (!issueItems.isEmpty()) {
                autoIssueDTO.setMaterials(issueItems);
                logger.info("Auto-Backflushing {} materials for WorkOrder {}", issueItems.size(), workOrder.getWorkOrderNumber());
                this.issueMaterials(autoIssueDTO);
            }
        }

        // Validate that all required material quantities are met
        // Check: issuedQty - scrappedQty (good consumed) >= netRequiredQuantity
        List<WorkOrderMaterial> materials = workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());

        for (WorkOrderMaterial material : materials) {
            if (material.getDeletedDate() != null) continue;

            BigDecimal issued = material.getIssuedQuantity() != null
                    ? material.getIssuedQuantity() : BigDecimal.ZERO;
            BigDecimal scrapped = material.getScrappedQuantity() != null
                    ? material.getScrappedQuantity() : BigDecimal.ZERO;
            BigDecimal goodIssued = issued.subtract(scrapped);

            if (goodIssued.compareTo(material.getNetRequiredQuantity()) < 0) {
                logger.warn(
                        "Cannot complete WorkOrder {}: material {} has insufficient good qty. Required: {}, Good issued: {} (issued {} - scrapped {})",
                        workOrder.getWorkOrderNumber(),
                        material.getComponent().getItemCode(),
                        material.getNetRequiredQuantity(),
                        goodIssued,
                        issued,
                        scrapped
                );
                throw new IllegalStateException(
                        "Material '" + material.getComponent().getItemCode() +
                        "' has insufficient good quantity. Required: " + material.getNetRequiredQuantity() +
                        ", Available: " + goodIssued
                );
            }
        }

        // Finalize quantities
        BigDecimal totalCompleted = refreshCompletedQuantities(workOrder);

        // ── Produce finished goods, one line at a time ───────────────────────────
        // Cost is pooled PER LINE and never across lines. A blended work-order unit cost would
        // value every item at the average of all of them — on a work order making 10 pumps and
        // 40 flanges that smears five-figure pump cost across trivial parts, corrupting finished
        // goods valuation and, through it, COGS.
        List<WorkOrderOperation> operations =
                workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());
        BigDecimal producedAcrossLines = produceFinishedGoodsPerLine(workOrder, materials, operations);

        workOrder.setCompletedQuantity(totalCompleted);

        //  Complete Work Order. With several lines the header reflects the least-advanced one:
        //  a line that produced nothing must not be hidden behind a COMPLETED header.
        workOrder.setWorkOrderStatus(deriveHeaderStatus(workOrder));
        workOrder.setActualEndDate(new Date());

        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} COMPLETED successfully with completedQty={} ({} units produced across {} line(s))",
                workOrder.getWorkOrderNumber(),
                totalCompleted,
                producedAcrossLines,
                workOrder.activeLines().size()
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

//        if (opInProgress) {
//            logger.warn(
//                    "Cancel blocked: operation IN_PROGRESS for WorkOrder {}",
//                    workOrder.getWorkOrderNumber()
//            );
//            throw new IllegalStateException(
//                    "Cannot cancel WorkOrder while an operation is in progress"
//            );
//        }

        // RETURN reserved stock to available for each material
        List<WorkOrderMaterial> materials = workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());
        for (WorkOrderMaterial material : materials) {
            if (material.getDeletedDate() != null) continue;
            BigDecimal consumed = material.getConsumedQuantity() != null ? material.getConsumedQuantity() : BigDecimal.ZERO;
            BigDecimal scrapped = material.getScrappedQuantity() != null ? material.getScrappedQuantity() : BigDecimal.ZERO;
            BigDecimal toReturn = material.getPlannedRequiredQuantity().subtract(consumed).subtract(scrapped);
            if (toReturn.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    InventoryTransactionDTO returnDto = new InventoryTransactionDTO();
                    returnDto.setInventoryItemId(material.getComponent().getInventoryItemId());
                    returnDto.setQuantity(toReturn.doubleValue());
                    returnDto.setTransactionType("RETURN");
                    returnDto.setReferenceDocNo(workOrder.getWorkOrderNumber());
                    inventoryTransactionService.returnStock(returnDto);
                } catch (Exception e) {
                    logger.warn("Could not return stock for material {} on WO cancel: {}", material.getComponent().getItemCode(), e.getMessage());
                }
            }
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
    public void shortCloseWorkOrder(int workOrderId, String remarks) {

        logger.info("Short-closing WorkOrder id={}", workOrderId);

        // ── Fetch Work Order ─────────────────────────────────────────────────
        WorkOrder workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> {
                    logger.error("WorkOrder not found id={}", workOrderId);
                    return new EntityNotFoundException("WorkOrder not found");
                });

        WorkOrderStatus currentStatus = workOrder.getWorkOrderStatus();

        // ── Status guard: can short-close from any active production status ──
        if (currentStatus != WorkOrderStatus.READY_FOR_PRODUCTION
                && currentStatus != WorkOrderStatus.PARTIALLY_READY
                && currentStatus != WorkOrderStatus.IN_PROGRESS
                && currentStatus != WorkOrderStatus.RELEASED) {
            logger.warn(
                    "Short-close rejected for WorkOrder {} due to status {}",
                    workOrder.getWorkOrderNumber(),
                    currentStatus
            );
            throw new IllegalStateException(
                    "WorkOrder can only be short-closed when READY_FOR_PRODUCTION, PARTIALLY_READY, or IN_PROGRESS. Current: " + currentStatus
            );
        }

        List<WorkOrderMaterial> materials = workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());
        List<WorkOrderOperation> operations = workOrderOperationRepository.findByWorkOrderIdOrderBySequence(workOrder.getId());

        // ── Step 1: Backflush if enabled ─────────────────────────────────────
        // For backflush WOs, auto-issue materials proportional to what was
        // actually completed so that cost calculations are accurate.
        if (workOrder.isAllowBackflush()) {
            BigDecimal totalCompleted = refreshCompletedQuantities(workOrder);
            BigDecimal plannedQty = workOrder.getPlannedQuantity();

            if (totalCompleted.compareTo(BigDecimal.ZERO) > 0 && plannedQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal completionRatio = totalCompleted.divide(plannedQty, 10, RoundingMode.HALF_UP);

                IssueWorkOrderMaterialDTO autoIssueDTO = new IssueWorkOrderMaterialDTO();
                autoIssueDTO.setWorkOrderId(workOrder.getId());
                List<IssueWorkOrderMaterialDTO.MaterialIssueItem> issueItems = new ArrayList<>();

                for (WorkOrderMaterial material : materials) {
                    if (material.getDeletedDate() != null) continue;

                    BigDecimal issued = material.getIssuedQuantity() != null ? material.getIssuedQuantity() : BigDecimal.ZERO;
                    BigDecimal proportionalNeed = material.getNetRequiredQuantity()
                            .multiply(completionRatio)
                            .setScale(5, RoundingMode.HALF_UP);
                    BigDecimal gap = proportionalNeed.subtract(issued);

                    if (gap.compareTo(BigDecimal.ZERO) > 0) {
                        IssueWorkOrderMaterialDTO.MaterialIssueItem item = new IssueWorkOrderMaterialDTO.MaterialIssueItem();
                        item.setWorkOrderMaterialId(material.getId());
                        item.setIssuedQuantity(gap);
                        item.setScrappedQuantity(BigDecimal.ZERO);
                        issueItems.add(item);
                    }
                }

                if (!issueItems.isEmpty()) {
                    autoIssueDTO.setMaterials(issueItems);
                    logger.info("Short-close backflush: auto-issuing {} materials for WorkOrder {}",
                            issueItems.size(), workOrder.getWorkOrderNumber());
                    try {
                        this.issueMaterials(autoIssueDTO);
                    } catch (Exception e) {
                        logger.warn("Backflush during short-close failed for WorkOrder {}: {}",
                                workOrder.getWorkOrderNumber(), e.getMessage());
                    }
                    // Refresh materials after auto-issue
                    materials = workOrderMaterialRepository.findByWorkOrderId(workOrder.getId());
                }
            }
        }

        // ── Step 2: Add finished goods to inventory (partial output) ─────────
        BigDecimal totalCompleted = refreshCompletedQuantities(workOrder);

        // Partial output is banked line by line, each at its own cost — same rule as a full
        // completion. A blended work-order rate would value unlike items identically.
        for (WorkOrderLine line : workOrder.activeLines()) {
            List<WorkOrderMaterial> lineMaterials = materials.stream()
                    .filter(m -> m.getDeletedDate() == null)
                    .filter(m -> belongsToLine(m.getWorkOrderLine(), line))
                    .toList();
            List<WorkOrderOperation> lineOperations = operations.stream()
                    .filter(o -> o.getDeletedDate() == null)
                    .filter(o -> belongsToLine(o.getWorkOrderLine(), line))
                    .toList();

            BigDecimal lineCompleted = calculateLineCompletedQuantity(line, lineOperations, lineMaterials);
            line.setCompletedQuantity(lineCompleted);
            workOrderLineRepository.save(line);

            if (lineCompleted.compareTo(BigDecimal.ZERO) <= 0 || line.getInventoryItem() == null) {
                continue;
            }

            BigDecimal lineCost = materialCostOf(lineMaterials).add(operationCostOf(lineOperations));
            BigDecimal realUnitCost = lineCost.divide(lineCompleted, 2, RoundingMode.HALF_UP);

            com.nextgenmanager.nextgenmanager.Inventory.dto.AddInventoryRequest addInvReq =
                    new com.nextgenmanager.nextgenmanager.Inventory.dto.AddInventoryRequest();
            addInvReq.setInventoryItemId(line.getInventoryItem().getInventoryItemId());
            addInvReq.setProcurementDecision(
                    com.nextgenmanager.nextgenmanager.Inventory.model.ProcurementDecision.WORK_ORDER);
            addInvReq.setReferenceId((long) workOrder.getId());
            addInvReq.setQuantity(lineCompleted.doubleValue());
            addInvReq.setCostPerUnit(realUnitCost.doubleValue());
            addInvReq.setCreatedBy("System");
            inventoryInstanceService.addInventory(addInvReq);

            logger.info("Short-close: Added {} units of {} to inventory for WorkOrder {} line {} with cost ₹{}",
                    lineCompleted, line.getInventoryItem().getItemCode(),
                    workOrder.getWorkOrderNumber(), line.getLineNumber(), realUnitCost);
        }

        workOrder.setCompletedQuantity(totalCompleted);

        // ── Step 3: Return unused materials to store ─────────────────────────
        // Materials that were issued but not consumed should go back to inventory
        for (WorkOrderMaterial material : materials) {
            if (material.getDeletedDate() != null) continue;

            BigDecimal consumed = material.getConsumedQuantity() != null ? material.getConsumedQuantity() : BigDecimal.ZERO;
            BigDecimal scrapped = material.getScrappedQuantity() != null ? material.getScrappedQuantity() : BigDecimal.ZERO;
            // Return everything reserved but not yet consumed: planned - consumed - scrapped
            // This covers both: issued-to-floor-but-not-consumed AND reserved-but-not-issued
            BigDecimal toReturn = material.getPlannedRequiredQuantity().subtract(consumed).subtract(scrapped);

            if (toReturn.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    InventoryTransactionDTO returnDto = new InventoryTransactionDTO();
                    returnDto.setInventoryItemId(material.getComponent().getInventoryItemId());
                    returnDto.setQuantity(toReturn.doubleValue());
                    returnDto.setTransactionType("RETURN");
                    returnDto.setReferenceType("WORK_ORDER");
                    returnDto.setReferenceDocNo(workOrder.getWorkOrderNumber());
                    inventoryTransactionService.returnStock(returnDto);

                    logger.info("Short-close: Returned {} units of {} | WO {}",
                            toReturn, material.getComponent().getItemCode(), workOrder.getWorkOrderNumber());
                    auditService.record(workOrder, WorkOrderEventType.MATERIAL_RETURNED,
                            "material", material.getComponent().getItemCode(),
                            toReturn.toPlainString(), "Returned uncommitted material on short-close");
                } catch (Exception e) {
                    logger.warn("Could not return material {} for WO short-close: {}",
                            material.getComponent().getItemCode(), e.getMessage());
                }
            }
        }

        // ── Step 4: Cancel non-completed operations ──────────────────────────
        for (WorkOrderOperation op : operations) {
            if (op.getStatus() != OperationStatus.COMPLETED) {
                op.setStatus(OperationStatus.CANCELLED);
            }
        }
        workOrderOperationRepository.saveAll(operations);

        // ── Step 5: Finalize work order ──────────────────────────────────────
        workOrder.setWorkOrderStatus(WorkOrderStatus.SHORT_CLOSED);
        workOrder.setActualEndDate(new Date());
        workOrder.setRemarks(
                (workOrder.getRemarks() != null ? workOrder.getRemarks() + " | " : "")
                        + "SHORT-CLOSED: " + (remarks != null ? remarks : "No reason provided")
        );
        workOrderRepository.save(workOrder);

        logger.info(
                "WorkOrder {} SHORT_CLOSED successfully with completedQty={} out of plannedQty={}",
                workOrder.getWorkOrderNumber(),
                totalCompleted,
                workOrder.getPlannedQuantity()
        );

        auditService.record(
                workOrder,
                WorkOrderEventType.SHORT_CLOSED,
                "status",
                currentStatus.toString(),
                "SHORT_CLOSED",
                "WorkOrder Short-Closed. Reason: " + (remarks != null ? remarks : "N/A")
                        + ". Completed: " + totalCompleted + "/" + workOrder.getPlannedQuantity()
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

    // ──────────────────────────────── Scheduling ────────────────────────────────

    @Autowired
    private com.nextgenmanager.nextgenmanager.production.service.scheduling.ProductionSchedulerService productionSchedulerService;

    @Override
    @Transactional
    public ScheduleResultDTO scheduleWorkOrder(int workOrderId) {
        return productionSchedulerService.scheduleWorkOrder(workOrderId);
    }

    @Override
    @Transactional
    public ScheduleResultDTO rescheduleWorkOrder(int workOrderId, Date newStartDate) {
        return productionSchedulerService.rescheduleWorkOrder(workOrderId, newStartDate);
    }

    // ──────────────────────────────── Material Re-order ──────────────────────────

    @Override
    @Transactional
    public WorkOrderMaterialReorderDTO reorderMaterial(Long workOrderId, Long materialId, ReorderMaterialRequestDTO dto) {

        WorkOrder workOrder = workOrderRepository.findById(workOrderId.intValue())
                .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found with ID: " + workOrderId));

        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.CREATED
                || workOrder.getWorkOrderStatus() == WorkOrderStatus.SCHEDULED
                || workOrder.getWorkOrderStatus() == WorkOrderStatus.COMPLETED
                || workOrder.getWorkOrderStatus() == WorkOrderStatus.CLOSED
                || workOrder.getWorkOrderStatus() == WorkOrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot re-order materials for a work order with status: " + workOrder.getWorkOrderStatus());
        }

        com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial material =
                workOrderMaterialRepository.findById(materialId)
                        .orElseThrow(() -> new EntityNotFoundException("Material not found with ID: " + materialId));

        if (material.getWorkOrder().getId() != workOrderId.intValue()) {
            throw new IllegalArgumentException("Material does not belong to this work order");
        }

        if (dto.getRequestedQuantity() == null || dto.getRequestedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Requested quantity must be greater than zero");
        }

        BigDecimal shortfall = material.getPlannedRequiredQuantity()
                .subtract(material.getIssuedQuantity())
                .max(BigDecimal.ZERO);

        long reorderCount = workOrderMaterialReorderRepository.countByWorkOrderMaterialId(materialId);

        com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest mr =
                new com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest();
        mr.setRequestSource(com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource.WORK_ORDER);
        mr.setSourceId((long) workOrder.getId());
        mr.setInventoryItem(material.getComponent());
        mr.setRequestedQuantity(dto.getRequestedQuantity());
        mr.setApprovalStatus(com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.PENDING);
        mr.setRequestedDate(new Date());
        mr.setReferenceNumber(workOrder.getWorkOrderNumber() + "-MAT-" + materialId + "-R" + (reorderCount + 1));
        mr.setRequestRemarks(dto.getRemarks());
        try {
            mr.setRequestedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception ignored) {
            mr.setRequestedBy("SYSTEM");
        }
        mr = inventoryRequestRepository.save(mr);

        com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterialReorder reorder =
                new com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterialReorder();
        reorder.setWorkOrderMaterial(material);
        reorder.setInventoryRequestId(mr.getId());
        reorder.setRequestedQuantity(dto.getRequestedQuantity());
        reorder.setShortfallQuantity(shortfall);
        reorder.setRemarks(dto.getRemarks());
        try {
            reorder.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception ignored) {
            reorder.setCreatedBy("SYSTEM");
        }
        reorder = workOrderMaterialReorderRepository.save(reorder);

        // Flag WO as waiting for additional material
        if (workOrder.getWorkOrderStatus() == WorkOrderStatus.IN_PROGRESS) {
            workOrder.setWorkOrderStatus(WorkOrderStatus.MATERIAL_REORDER);
            workOrderRepository.save(workOrder);
            logger.info("WO {} transitioned to MATERIAL_REORDER", workOrder.getWorkOrderNumber());
        }

        logger.info("Material re-order created for WO {} material {} quantity {} ref {}",
                workOrder.getWorkOrderNumber(), materialId, dto.getRequestedQuantity(), mr.getReferenceNumber());

        return buildReorderDTO(reorder, mr);
    }

    @Override
    public List<WorkOrderMaterialReorderDTO> getMaterialReorders(Long workOrderId, Long materialId) {

        com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial material =
                workOrderMaterialRepository.findById(materialId)
                        .orElseThrow(() -> new EntityNotFoundException("Material not found with ID: " + materialId));

        if (material.getWorkOrder().getId() != workOrderId.intValue()) {
            throw new IllegalArgumentException("Material does not belong to this work order");
        }

        return workOrderMaterialReorderRepository
                .findByWorkOrderMaterialIdOrderByCreatedDateDesc(materialId)
                .stream()
                .map(reorder -> {
                    com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest mr = null;
                    if (reorder.getInventoryRequestId() != null) {
                        mr = inventoryRequestRepository.findById(reorder.getInventoryRequestId()).orElse(null);
                    }
                    return buildReorderDTO(reorder, mr);
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Extra processable production units unlocked by approved reorders for this operation's materials.
     * Calculated as min(approvedReorderQty / reqPerUnit) across all materials — the bottleneck material
     * limits how many extra units can actually be produced.
     * Only meaningful for the first operation (where raw material = direct input).
     */
    private BigDecimal extraInputFromReorders(WorkOrderOperation operation, WorkOrder workOrder) {
        List<WorkOrderMaterial> opMaterials =
                new ArrayList<>(workOrderMaterialRepository.findByWorkOrderOperationId(operation.getId()));

        boolean isFirstOp = workOrderOperationRepository
                .findTopByWorkOrderLineAndSequenceLessThanOrderBySequenceDesc(
                        operation.getWorkOrderLine(), operation.getSequence()) == null;
        if (isFirstOp) {
            opMaterials.addAll(workOrderMaterialRepository.findByWorkOrderAndWorkOrderOperationIsNull(workOrder));
        }

        if (opMaterials.isEmpty()) return BigDecimal.ZERO;

        BigDecimal woPlannedQty = workOrder.getPlannedQuantity();
        if (woPlannedQty.compareTo(BigDecimal.ZERO) <= 0) woPlannedQty = BigDecimal.ONE;

        BigDecimal minExtra = null;
        for (WorkOrderMaterial material : opMaterials) {
            if (material.getDeletedDate() != null) continue;
            BigDecimal reqPerUnit = material.getNetRequiredQuantity()
                    .divide(woPlannedQty, 10, RoundingMode.HALF_UP);
            if (reqPerUnit.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal approvedReorder = workOrderMaterialReorderRepository
                    .findByWorkOrderMaterialIdOrderByCreatedDateDesc(material.getId())
                    .stream()
                    .filter(r -> r.getInventoryRequestId() != null)
                    .map(r -> inventoryRequestRepository.findById(r.getInventoryRequestId()).orElse(null))
                    .filter(r -> r != null
                            && (r.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.APPROVED
                            || r.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.PARTIAL))
                    .map(r -> r.getApprovedQuantity() != null ? r.getApprovedQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal extraForMaterial = approvedReorder.divide(reqPerUnit, 5, RoundingMode.FLOOR);
            minExtra = (minExtra == null) ? extraForMaterial : minExtra.min(extraForMaterial);
        }

        return minExtra != null ? minExtra.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    /**
     * Actual approved quantity available for issuance (original MR + reorder MRs).
     * Used by auto-backflush to enforce the Stores approval gate even when materials
     * are auto-issued at consumption time.
     */
    private BigDecimal approvedIssueCap(com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial material) {
        BigDecimal originalApproved = BigDecimal.ZERO;
        if (material.getInventoryRequestId() != null) {
            com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest mr =
                    inventoryRequestRepository.findById(material.getInventoryRequestId()).orElse(null);
            if (mr != null
                    && (mr.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.APPROVED
                    || mr.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.PARTIAL)
                    && mr.getApprovedQuantity() != null) {
                originalApproved = mr.getApprovedQuantity();
            }
        }

        BigDecimal reorderApproved = workOrderMaterialReorderRepository
                .findByWorkOrderMaterialIdOrderByCreatedDateDesc(material.getId())
                .stream()
                .filter(r -> r.getInventoryRequestId() != null)
                .map(r -> inventoryRequestRepository.findById(r.getInventoryRequestId()).orElse(null))
                .filter(r -> r != null
                        && (r.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.APPROVED
                        || r.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.PARTIAL))
                .map(r -> r.getApprovedQuantity() != null ? r.getApprovedQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return originalApproved.add(reorderApproved);
    }

    private BigDecimal effectiveIssueCap(com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial material) {
        BigDecimal approvedReorderQty = workOrderMaterialReorderRepository
                .findByWorkOrderMaterialIdOrderByCreatedDateDesc(material.getId())
                .stream()
                .filter(r -> r.getInventoryRequestId() != null)
                .map(r -> inventoryRequestRepository.findById(r.getInventoryRequestId()).orElse(null))
                .filter(r -> r != null && (r.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.APPROVED
                        || r.getApprovalStatus() == com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus.PARTIAL))
                .map(r -> r.getApprovedQuantity() != null ? r.getApprovedQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return material.getPlannedRequiredQuantity().add(approvedReorderQty);
    }

    private WorkOrderMaterialReorderDTO buildReorderDTO(
            com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterialReorder reorder,
            com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest mr) {

        WorkOrderMaterialReorderDTO dto = new WorkOrderMaterialReorderDTO();
        dto.setId(reorder.getId());
        dto.setWorkOrderMaterialId(reorder.getWorkOrderMaterial().getId());
        dto.setMaterialCode(reorder.getWorkOrderMaterial().getComponent().getItemCode());
        dto.setMaterialName(reorder.getWorkOrderMaterial().getComponent().getName());
        dto.setInventoryRequestId(reorder.getInventoryRequestId());
        dto.setRequestedQuantity(reorder.getRequestedQuantity());
        dto.setShortfallQuantity(reorder.getShortfallQuantity());
        dto.setRemarks(reorder.getRemarks());
        dto.setCreatedDate(reorder.getCreatedDate());
        dto.setCreatedBy(reorder.getCreatedBy());
        if (mr != null) {
            dto.setMrStatus(mr.getApprovalStatus() != null ? mr.getApprovalStatus().name() : null);
            dto.setMrApprovedQuantity(mr.getApprovedQuantity());
            dto.setReferenceNumber(mr.getReferenceNumber());
        }
        return dto;
    }

}
