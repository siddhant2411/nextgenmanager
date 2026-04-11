package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.service.FileStorageService;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDependencyDTO;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.production.helper.InvalidTransitionException;
import com.nextgenmanager.nextgenmanager.production.enums.DependencyType;
import com.nextgenmanager.nextgenmanager.production.enums.RoutingStatus;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.mapper.RoutingMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.RoutingOperationMapper;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperationDependency;
import com.nextgenmanager.nextgenmanager.production.repository.RoutingOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.RoutingRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.EventPublisher;
import com.nextgenmanager.nextgenmanager.production.service.audit.RoutingAuditService;
import io.minio.GetObjectResponse;
import jakarta.xml.bind.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.nextgenmanager.nextgenmanager.assets.service.MachineDetailsService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoutingServiceImpl implements RoutingService{

    @Autowired
    private  BomRepository bomRepository;

    @Autowired
    private  RoutingRepository routingRepository;

    @Autowired
    private  RoutingOperationRepository routingOperationRepository;

    @Autowired
    private  WorkCenterService workCenterService;

    @Autowired
    private RoutingAuditService auditService;

    @Autowired
    private RoutingMapper routingMapper;
    @Autowired
    private ProductionJobService productionJobService;


    @Autowired
    private LaborRoleService laborRoleService;

    @Autowired
    private MachineDetailsService machineDetailsService;

    @Autowired
    private RoutingOperationMapper routingOperationMapper;

    @Autowired
    private FileStorageService fileStorageService;

    private EventPublisher eventPublisher;

    private static final Map<RoutingStatus, Set<RoutingStatus>> ALLOWED = Map.of(
            RoutingStatus.DRAFT, Set.of(RoutingStatus.APPROVED, RoutingStatus.OBSOLETE),
            RoutingStatus.APPROVED, Set.of(RoutingStatus.ACTIVE, RoutingStatus.DRAFT),
            RoutingStatus.ACTIVE, Set.of(RoutingStatus.OBSOLETE),
            RoutingStatus.OBSOLETE, Set.of()
    );


    // ----------------------------------------------------------
    // CREATE or UPDATE ROUTING
    // ----------------------------------------------------------
    @Override
    @Transactional
    public RoutingDto createOrUpdateRouting(Integer bomId, RoutingDto dto, String actor) {

        Bom bom = bomRepository.findById(bomId)
                .orElseThrow(() -> new ResourceNotFoundException("BOM not found: " + bomId));

        Routing routing = routingRepository.findByBomId(bomId)
                .orElse(new Routing());

        routing.setBom(bom);
        routing.setStatus(dto.getStatus() != null ? dto.getStatus() : RoutingStatus.DRAFT);
        routing.setCreatedBy(routing.getCreatedBy() == null ? actor : routing.getCreatedBy());
        routing.setUpdatedDate(new Date());

        // Update operations in-place to preserve IDs (BOM positions reference them via FK)
        Map<Long, RoutingOperation> existingById = routing.getOperations().stream()
                .filter(op -> op.getId() != null)
                .collect(java.util.stream.Collectors.toMap(RoutingOperation::getId, op -> op));

        List<RoutingOperation> updatedOps = new java.util.ArrayList<>();
        int seq = 1;
        for (RoutingOperationDto opDto : dto.getOperations()) {
            RoutingOperation op;
            if (opDto.getId() != null && existingById.containsKey(opDto.getId())) {
                // Update existing operation in-place — preserves its ID
                op = existingById.get(opDto.getId());
            } else {
                // New operation
                op = new RoutingOperation();
                op.setRouting(routing);
            }

            op.setSequenceNumber(
                    opDto.getSequenceNumber() != null ? opDto.getSequenceNumber() : seq++
            );
            op.setName(opDto.getName());

            if (opDto.getProductionJob() != null) {
                op.setProductionJob(productionJobService.getProductionJobEntityById(opDto.getProductionJob().getId()));
            } else {
                op.setProductionJob(null);
            }

            if (opDto.getWorkCenter() != null) {
                op.setWorkCenter(workCenterService.getWorkCenterEntityById(opDto.getWorkCenter().getId()));
            } else {
                op.setWorkCenter(null);
            }

            if (opDto.getLaborRole() != null) {
                op.setLaborRole(laborRoleService.getEntityById(opDto.getLaborRole().getId()));
            } else {
                op.setLaborRole(null);
            }

            op.setNumberOfOperators(opDto.getNumberOfOperators() != null ? opDto.getNumberOfOperators() : 1);

            if (opDto.getMachineDetails() != null) {
                op.setMachineDetails(machineDetailsService.getMachineDetailsEntityById(opDto.getMachineDetails().getId()));
            } else {
                op.setMachineDetails(null);
            }

            op.setCostType(opDto.getCostType());
            op.setFixedCostPerUnit(opDto.getFixedCostPerUnit());
            op.setSetupTime(opDto.getSetupTime());
            op.setRunTime(opDto.getRunTime());
            op.setInspection(opDto.getInspection());
            op.setNotes(opDto.getNotes());

            // Parallel operation fields
            op.setAllowParallel(Boolean.TRUE.equals(opDto.getAllowParallel()));
            op.setParallelPath(opDto.getParallelPath());

            updatedOps.add(op);
        }

        // Remove operations no longer in the incoming list (orphanRemoval will delete them)
        routing.getOperations().removeIf(op -> !updatedOps.contains(op));

        // Add new operations that aren't already in the list
        for (RoutingOperation op : updatedOps) {
            if (!routing.getOperations().contains(op)) {
                routing.getOperations().add(op);
            }
        }

        // First save — ensures all operations have IDs (needed for dependency resolution)
        Routing saved = routingRepository.save(routing);

        // Resolve and persist operation dependencies
        resolveDependencies(saved, dto.getOperations());
        validateNoCycles(saved);
        saved = routingRepository.save(saved);

        auditService.audit("ROUTING_CREATE_OR_UPDATE", actor,
                "routingId=" + saved.getId() + ", bom=" + bomId);

        return routingMapper.toDTO(saved);
    }

    // ----------------------------------------------------------
    // UPDATE OPERATIONS ONLY
    // ----------------------------------------------------------
    @Override
    @Transactional
    public Routing updateOperations(Long routingId, List<RoutingOperationDto> operations, String actor) {

        Routing routing = routingRepository.findById(routingId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found: " + routingId));

        if (!routing.isEditable()) {
            throw new InvalidTransitionException("Cannot edit routing in status: " +routing.getStatus());
        }

        // Update operations in-place to preserve IDs (BOM positions reference them via FK)
        Map<Long, RoutingOperation> existingById = routing.getOperations().stream()
                .filter(op -> op.getId() != null)
                .collect(java.util.stream.Collectors.toMap(RoutingOperation::getId, op -> op));

        List<RoutingOperation> updatedOps = new java.util.ArrayList<>();
        int seq = 1;
        for (RoutingOperationDto opDto : operations) {
            RoutingOperation op;
            if (opDto.getId() != null && existingById.containsKey(opDto.getId())) {
                op = existingById.get(opDto.getId());
            } else {
                op = new RoutingOperation();
                op.setRouting(routing);
            }

            op.setSequenceNumber(opDto.getSequenceNumber() != null ? opDto.getSequenceNumber() : seq++);
            op.setName(opDto.getName());
            if (opDto.getWorkCenter() != null) {
                op.setWorkCenter(workCenterService.getWorkCenterEntityById(opDto.getWorkCenter().getId()));
            } else {
                op.setWorkCenter(null);
            }
            if (opDto.getLaborRole() != null) {
                op.setLaborRole(laborRoleService.getEntityById(opDto.getLaborRole().getId()));
            } else {
                op.setLaborRole(null);
            }

            op.setNumberOfOperators(opDto.getNumberOfOperators() != null ? opDto.getNumberOfOperators() : 1);

            if (opDto.getMachineDetails() != null) {
                op.setMachineDetails(machineDetailsService.getMachineDetailsEntityById(opDto.getMachineDetails().getId()));
            } else {
                op.setMachineDetails(null);
            }

            op.setCostType(opDto.getCostType());
            op.setFixedCostPerUnit(opDto.getFixedCostPerUnit());
            op.setSetupTime(opDto.getSetupTime());
            op.setRunTime(opDto.getRunTime());
            op.setInspection(opDto.getInspection());
            op.setNotes(opDto.getNotes());

            // Parallel operation fields
            op.setAllowParallel(Boolean.TRUE.equals(opDto.getAllowParallel()));
            op.setParallelPath(opDto.getParallelPath());

            updatedOps.add(op);
        }

        routing.getOperations().removeIf(op -> !updatedOps.contains(op));
        for (RoutingOperation op : updatedOps) {
            if (!routing.getOperations().contains(op)) {
                routing.getOperations().add(op);
            }
        }

        // First save — ensures all operations have IDs
        Routing saved = routingRepository.save(routing);

        // Resolve and persist operation dependencies
        resolveDependencies(saved, operations);
        validateNoCycles(saved);
        saved = routingRepository.save(saved);

        auditService.audit("ROUTING_UPDATE_OPERATIONS", actor, "routingId=" + routingId);

        return saved;
    }



    @Override
    @Transactional
    public void approve(Long routingId, String actor) throws ValidationException {
        Routing routing = routingRepository.findById(routingId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found: " + routingId));

        if (routing.getStatus() != RoutingStatus.DRAFT) {
            throw new InvalidTransitionException("Only DRAFT routing can be approved.");
        }

        validateForApproval(routing);

        changeStatus(routing, RoutingStatus.APPROVED, actor);
    }


    // ----------------------------------------------------------
    // ACTIVATE → Used by Work Orders
    // ----------------------------------------------------------
    @Override
    @Transactional
    public void activate(Long routingId, String actor) throws ValidationException {

        Routing routing = routingRepository.findById(routingId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found: " + routingId));

        if (routing.getStatus() != RoutingStatus.APPROVED) {
            throw new InvalidTransitionException("Only APPROVED routing can be activated.");
        }

        validateForActivation(routing);

        changeStatus(routing, RoutingStatus.ACTIVE, actor);

        eventPublisher.publish("ROUTING_ACTIVATED", routingId); // optional
    }


    // ----------------------------------------------------------
    // OBSOLETE
    // ----------------------------------------------------------
    @Override
    @Transactional
    public void obsolete(Long routingId, String actor) {
        Routing routing = routingRepository.findById(routingId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found: " + routingId));

        changeStatus(routing, RoutingStatus.OBSOLETE, actor);
    }


    // ----------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------
    @Override
    public RoutingDto getByBom(Integer bomId) {
        Routing routing =routingRepository.findByBomId(bomId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found for BOM: " + bomId));
        routing.setOperations(getOperationsEntities(routing.getId()));

        RoutingDto dto = routingMapper.toDTO(routing);
        enrichOperationAttachments(dto);
        return dto;
    }

    @Override
    public Routing getRoutingEntityByBom(Integer bomId){

        Routing routing =routingRepository.findByBomId(bomId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found for BOM: " + bomId));
        routing.setOperations(getOperationsEntities(routing.getId()));
        return routing;
    }

    @Override
    public RoutingDto getRouting(Long id) {
        Routing routing =routingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found for BOM: " + id));
        RoutingDto dto = routingMapper.toDTO(routing);
        enrichOperationAttachments(dto);
        return dto;
    }

    @Override
    public List<RoutingOperationDto> getOperations(Long routingId) {
        Routing routing = routingRepository.findById(routingId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found: " + routingId));
        List<RoutingOperation> routingOperations = routing.getOperations();
        return routingOperations.stream().map(op -> {
            RoutingOperationDto dto = routingOperationMapper.toDTO(op);
            if (op.getId() != null) {
                dto.setAttachments(fileStorageService.findAttachmentsByTypeAndId("RoutingOperation", op.getId()));
            }
            return dto;
        }).toList();
    }


    @Override
    public List<RoutingOperation> getOperationsEntities(Long routingId) {
        Routing routing = routingRepository.findById(routingId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found: " + routingId));
        List<RoutingOperation> routingOperations = routing.getOperations();
        return routingOperations;
    }

    @Override
    public Long findRoutingIdByBom(int bomId) {
        return routingRepository.findByBomId(bomId)
                .map(Routing::getId)
                .orElse(null);
    }

    @Override
    public Long getRoutingIdForOperation(Long operationId) {
        RoutingOperation op = routingOperationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingOperation not found: " + operationId));
        return op.getRouting().getId();
    }


    // ----------------------------------------------------------
    // Helper: Dependency Resolution
    // ----------------------------------------------------------

    /**
     * Resolves dependency DTOs into {@link RoutingOperationDependency} entities
     * and replaces each operation's existing dependency list.
     *
     * <p>Looks up the upstream operation by ID first; falls back to sequence number
     * so that newly-created operations (no client-side ID yet) can still declare deps.
     *
     * <p>Self-dependencies are silently skipped.
     * Duplicate pairs are skipped (the unique constraint on the table also enforces this).
     *
     * @param routing  the saved routing whose operations already have IDs
     * @param opDtos   the request DTOs in the same positional order as the operations
     */
    private void resolveDependencies(Routing routing, List<RoutingOperationDto> opDtos) {

        // Build lookup maps from the saved operations
        Map<Long,    RoutingOperation> byId  = routing.getOperations().stream()
                .filter(op -> op.getId() != null)
                .collect(Collectors.toMap(RoutingOperation::getId, op -> op));

        Map<Integer, RoutingOperation> bySeq = routing.getOperations().stream()
                .filter(op -> op.getSequenceNumber() != null)
                .collect(Collectors.toMap(RoutingOperation::getSequenceNumber, op -> op,
                        (a, b) -> a)); // keep first on duplicate sequence (shouldn't happen)

        for (RoutingOperationDto opDto : opDtos) {
            // Find the matching saved operation for this DTO
            RoutingOperation op = null;
            if (opDto.getId() != null) {
                op = byId.get(opDto.getId());
            }
            if (op == null && opDto.getSequenceNumber() != null) {
                op = bySeq.get(opDto.getSequenceNumber());
            }
            if (op == null) continue;

            // Clear existing dependencies (orphanRemoval will delete old rows)
            op.getDependencies().clear();

            if (opDto.getDependencies() == null || opDto.getDependencies().isEmpty()) continue;

            Set<Long> addedPairs = new HashSet<>(); // prevent duplicate dep pairs

            for (RoutingOperationDependencyDTO depDto : opDto.getDependencies()) {
                // Resolve the upstream operation
                RoutingOperation upstream = null;
                if (depDto.getDependsOnRoutingOperationId() != null) {
                    upstream = byId.get(depDto.getDependsOnRoutingOperationId());
                }
                if (upstream == null && depDto.getDependsOnSequenceNumber() != null) {
                    upstream = bySeq.get(depDto.getDependsOnSequenceNumber());
                }
                if (upstream == null) continue;

                // Skip self-dependency
                if (upstream.getId().equals(op.getId())) continue;

                // Skip duplicate pair
                if (!addedPairs.add(upstream.getId())) continue;

                RoutingOperationDependency dep = new RoutingOperationDependency();
                dep.setRoutingOperation(op);
                dep.setDependsOnRoutingOperation(upstream);
                dep.setDependencyType(
                        depDto.getDependencyType() != null
                                ? depDto.getDependencyType()
                                : DependencyType.SEQUENTIAL
                );
                dep.setIsRequired(depDto.getIsRequired() != null ? depDto.getIsRequired() : true);

                op.getDependencies().add(dep);
            }
        }
    }

    /**
     * Validates that the routing's SEQUENTIAL dependencies form a DAG (no cycles).
     * Only SEQUENTIAL deps can cause deadlocks; PARALLEL_ALLOWED deps are ignored.
     *
     * @throws IllegalStateException if a cycle is detected
     */
    private void validateNoCycles(Routing routing) {

        // Build an adjacency map: opId → set of opIds it sequentially depends on
        Map<Long, Set<Long>> graph = new HashMap<>();
        for (RoutingOperation op : routing.getOperations()) {
            Set<Long> deps = op.getDependencies().stream()
                    .filter(d -> d.getDependencyType() == DependencyType.SEQUENTIAL)
                    .map(d -> d.getDependsOnRoutingOperation().getId())
                    .collect(Collectors.toSet());
            graph.put(op.getId(), deps);
        }

        Set<Long> visited = new HashSet<>();
        Set<Long> inStack = new HashSet<>();

        for (Long opId : graph.keySet()) {
            if (hasCycle(opId, graph, visited, inStack)) {
                throw new IllegalStateException(
                        "Circular dependency detected in routing operations. " +
                        "Please review the dependency configuration."
                );
            }
        }
    }

    /** DFS-based cycle detection. Returns true if a cycle is found starting from {@code opId}. */
    private boolean hasCycle(Long opId, Map<Long, Set<Long>> graph,
                              Set<Long> visited, Set<Long> inStack) {
        if (inStack.contains(opId)) return true;
        if (visited.contains(opId))  return false;

        visited.add(opId);
        inStack.add(opId);

        for (Long depId : graph.getOrDefault(opId, Set.of())) {
            if (hasCycle(depId, graph, visited, inStack)) return true;
        }

        inStack.remove(opId);
        return false;
    }

    // ----------------------------------------------------------
    // Helper: Status Change with Validation & Audit
    // ----------------------------------------------------------
    private void changeStatus(Routing routing, RoutingStatus to, String actor) {

        RoutingStatus from = routing.getStatus();

        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidTransitionException("Invalid transition: " + from + " → " + to);
        }

        routing.setStatus(to);
        routing.setUpdatedDate(new Date());
        routingRepository.save(routing);

        auditService.audit("ROUTING_STATUS_CHANGE", actor,
                "routingId=" + routing.getId() + ", from=" + from + ", to=" + to);

        eventPublisher.publish("ROUTING_STATUS_UPDATED", routing.getId());
    }


    // ----------------------------------------------------------
    // VALIDATION
    // ----------------------------------------------------------
    private void validateForApproval(Routing routing) throws ValidationException {

        if (routing.getOperations().isEmpty()) {
            throw new ValidationException("Routing must contain at least one operation.");
        }

        for (RoutingOperation op : routing.getOperations()) {
            if (op.getWorkCenter() == null) {
                throw new ValidationException("Operation " + op.getName() + " missing WorkCenter.");
            }
            if (op.getRunTime() == null || op.getRunTime().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Operation " + op.getName() + " has invalid run time.");
            }
        }
    }

    private void validateForActivation(Routing routing) throws ValidationException {
        // You can add deep validations or capacity checks
        validateForApproval(routing);
    }


    // ----------------------------------------------------------
    // Helper: Enrich operation DTOs with file attachments
    // ----------------------------------------------------------
    private void enrichOperationAttachments(RoutingDto dto) {
        if (dto.getOperations() == null) return;
        for (RoutingOperationDto opDto : dto.getOperations()) {
            if (opDto.getId() != null) {
                opDto.setAttachments(fileStorageService.findAttachmentsByTypeAndId("RoutingOperation", opDto.getId()));
            }
        }
    }

    // ----------------------------------------------------------
    // OPERATION ATTACHMENTS
    // ----------------------------------------------------------

    @Override
    public void uploadOperationAttachment(Long operationId, MultipartFile file) throws Exception {
        RoutingOperation op = routingOperationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingOperation not found: " + operationId));

        Routing routing = op.getRouting();
        if (!routing.isEditable()) {
            throw new InvalidTransitionException("Cannot add attachments — routing is in status: " + routing.getStatus());
        }

        fileStorageService.uploadFile(file, "routing-operation", "RoutingOperation", operationId, "SYSTEM");
    }

    @Override
    public List<FileAttachment> getOperationAttachments(Long operationId) {
        return fileStorageService.findAttachmentsByTypeAndId("RoutingOperation", operationId);
    }

    @Override
    @Transactional
    public void deleteOperationAttachment(Long operationId, Long fileId) throws Exception {
        RoutingOperation op = routingOperationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingOperation not found: " + operationId));

        Routing routing = op.getRouting();
        if (!routing.isEditable()) {
            throw new InvalidTransitionException("Cannot delete attachments — routing is in status: " + routing.getStatus());
        }

        fileStorageService.deleteAttachment(fileId);
    }

    @Override
    public GetObjectResponse downloadOperationAttachment(Long fileId) {
        try {
            return fileStorageService.downloadById(fileId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to download operation attachment", e);
        }
    }

    @Override
    public FileAttachment getOperationAttachment(Long fileId) {
        return fileStorageService.getFileById(fileId);
    }

}
