package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.production.helper.InvalidTransitionException;
import com.nextgenmanager.nextgenmanager.production.helper.RoutingStatus;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.mapper.RoutingMapper;
import com.nextgenmanager.nextgenmanager.production.mapper.RoutingOperationMapper;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.model.RoutingOperation;
import com.nextgenmanager.nextgenmanager.production.repository.RoutingOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.RoutingRepository;
import com.nextgenmanager.nextgenmanager.production.service.audit.EventPublisher;
import com.nextgenmanager.nextgenmanager.production.service.audit.RoutingAuditService;
import jakarta.xml.bind.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
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
    private RoutingOperationMapper routingOperationMapper;
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

        // Clear old operations & replace
        routing.getOperations().clear();

        int seq = 1;
        for (RoutingOperationDto opDto : dto.getOperations()) {
            RoutingOperation op = new RoutingOperation();
            op.setRouting(routing);
            op.setSequenceNumber(
                    opDto.getSequenceNumber() != null ? opDto.getSequenceNumber() : seq++
            );
            op.setName(opDto.getName());
            op.setProductionJob(productionJobService.getProductionJobEntityById(opDto.getProductionJob().getId()));
            op.setWorkCenter(workCenterService.getWorkCenterEntityById(opDto.getWorkCenter().getId()));
            op.setSetupTime(opDto.getSetupTime());
            op.setRunTime(opDto.getRunTime());
            op.setInspection(opDto.getInspection());
            op.setNotes(opDto.getNotes());
            routing.getOperations().add(op);
        }

        Routing saved = routingRepository.save(routing);

        auditService.audit("ROUTING_CREATE_OR_UPDATE", actor,
                "routingId=" + saved.getId() + ", bom=" + bomId);

        return routingMapper.toDTO(routing);
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

        routing.getOperations().clear();
        int seq = 1;
        for (RoutingOperationDto opDto : operations) {
            RoutingOperation op = new RoutingOperation();
            op.setRouting(routing);
            op.setSequenceNumber(opDto.getSequenceNumber() != null ? opDto.getSequenceNumber() : seq++);
            op.setName(opDto.getName());
            op.setWorkCenter(workCenterService.getWorkCenterEntityById(opDto.getWorkCenter().getId()));
            op.setSetupTime(opDto.getSetupTime());
            op.setRunTime(opDto.getRunTime());
            op.setInspection(opDto.getInspection());
            op.setNotes(opDto.getNotes());
            routing.getOperations().add(op);
        }

        Routing saved = routingRepository.save(routing);

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

        return routingMapper.toDTO(routing);
    }

    @Override
    public RoutingDto getRouting(Long id) {
        Routing routing =routingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found for BOM: " + id));
        return routingMapper.toDTO(routing);
    }

    @Override
    public List<RoutingOperationDto> getOperations(Long routingId) {
        Routing routing = routingRepository.findById(routingId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found: " + routingId));
        List<RoutingOperation> routingOperations = routing.getOperations();
        return routingOperations.stream().map(routingOperation -> routingOperationMapper.toDTO(routingOperation)).toList();
    }


    @Override
    public List<RoutingOperation> getOperationsEntities(Long routingId) {
        Routing routing = routingRepository.findById(routingId)
                .orElseThrow(() -> new ResourceNotFoundException("Routing not found: " + routingId));
        List<RoutingOperation> routingOperations = routing.getOperations();
        return routingOperations;
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


}
