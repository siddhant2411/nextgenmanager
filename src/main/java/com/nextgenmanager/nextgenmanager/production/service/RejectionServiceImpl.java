package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.DispositionRequestDTO;
import com.nextgenmanager.nextgenmanager.production.dto.RejectionEntryDTO;
import com.nextgenmanager.nextgenmanager.production.dto.YieldMetricsDTO;
import com.nextgenmanager.nextgenmanager.production.enums.DispositionStatus;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderSourceType;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.model.RejectionEntry;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.repository.RejectionEntryRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderOperationRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RejectionServiceImpl implements RejectionService {

    private static final Logger logger = LoggerFactory.getLogger(RejectionServiceImpl.class);

    @Autowired
    private RejectionEntryRepository rejectionEntryRepository;

    @Autowired
    private WorkOrderRepository workOrderRepository;

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    @Override
    @Transactional
    public void disposeRejection(DispositionRequestDTO dto) {
        if (dto.getRejectionEntryId() == null) {
            throw new IllegalArgumentException("Rejection entry ID is required");
        }
        if (dto.getDispositionStatus() == null) {
            throw new IllegalArgumentException("Disposition status is required");
        }
        if (dto.getDispositionStatus() == DispositionStatus.PENDING) {
            throw new IllegalArgumentException("Cannot set disposition to PENDING");
        }

        RejectionEntry rejection = rejectionEntryRepository.findById(dto.getRejectionEntryId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Rejection entry not found: " + dto.getRejectionEntryId()));

        if (rejection.getDispositionStatus() != DispositionStatus.PENDING) {
            throw new IllegalStateException(
                    "Rejection entry " + dto.getRejectionEntryId() + " is already disposed as "
                    + rejection.getDispositionStatus());
        }

        WorkOrder workOrder = rejection.getWorkOrder();

        // ─── Resolve disposition quantity (full or partial split) ─────────────────
        BigDecimal disposeQty = dto.getQuantity() != null
                ? dto.getQuantity() : rejection.getRejectedQuantity();
        if (disposeQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Disposition quantity must be greater than zero");
        }
        if (disposeQty.compareTo(rejection.getRejectedQuantity()) > 0) {
            throw new IllegalArgumentException(
                    "Disposition quantity (" + disposeQty + ") exceeds the rejection qty ("
                    + rejection.getRejectedQuantity() + ")");
        }

        boolean isPartial = disposeQty.compareTo(rejection.getRejectedQuantity()) < 0;

        // The "target" entry receives the disposition status.
        // For partial: split — reduce original and create a new entry for the disposed portion.
        RejectionEntry target;
        if (isPartial) {
            rejection.setRejectedQuantity(rejection.getRejectedQuantity().subtract(disposeQty));
            rejectionEntryRepository.save(rejection);

            target = new RejectionEntry();
            target.setWorkOrder(rejection.getWorkOrder());
            target.setOperation(rejection.getOperation());
            target.setRejectedQuantity(disposeQty);
            target.setCreatedBy(rejection.getCreatedBy());
        } else {
            target = rejection;
        }

        switch (dto.getDispositionStatus()) {
            case ACCEPT:
                // Concession: units accepted as-is. Material already consumed; no inventory change needed.
                // The rejected units now count as good output: move qty from rejected → completed
                // on the operation, forward to dependents, and refresh WO completed total.
                acceptRejectedQuantity(target);
                logger.info("Rejection {} accepted under concession for WO {} — {} units moved to completed",
                        target.getId(), workOrder.getWorkOrderNumber(), disposeQty);
                break;

            case REWORK:
                WorkOrder reworkWO = buildReworkWorkOrder(workOrder, disposeQty);
                workOrderRepository.save(reworkWO);
                target.setChildWorkOrder(reworkWO);
                logger.info("Rework WO {} created for rejection {} on WO {} — {} units",
                        reworkWO.getWorkOrderNumber(), target.getId(), workOrder.getWorkOrderNumber(), disposeQty);
                break;

            case SCRAP:
                // Material was already consumed at operation completion. Just record the disposition.
                logger.info("Rejection {} scrapped for WO {} — {} units",
                        target.getId(), workOrder.getWorkOrderNumber(), disposeQty);
                break;
        }

        String currentUser = getCurrentUser();
        target.setDispositionStatus(dto.getDispositionStatus());
        target.setDispositionReason(dto.getDispositionReason());
        target.setDisposedAt(new Date());
        target.setDisposedBy(currentUser);

        rejectionEntryRepository.save(target);
    }

    @Override
    public List<RejectionEntryDTO> listRejections(int workOrderId, DispositionStatus statusFilter) {
        List<RejectionEntry> entries = (statusFilter != null)
                ? rejectionEntryRepository.findByWorkOrderIdAndDispositionStatus(workOrderId, statusFilter)
                : rejectionEntryRepository.findByWorkOrderId(workOrderId);

        return entries.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public YieldMetricsDTO getYieldMetrics(int workOrderId) {
        WorkOrder wo = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Work order not found: " + workOrderId));

        YieldMetricsDTO dto = new YieldMetricsDTO();
        dto.setWorkOrderId(wo.getId());
        dto.setWorkOrderNumber(wo.getWorkOrderNumber());
        dto.setPlannedQuantity(wo.getPlannedQuantity());
        dto.setTotalGoodQuantity(wo.getTotalOperationGoodQuantity());
        dto.setTotalRejectedQuantity(wo.getTotalOperationRejectedQuantity());
        dto.setTotalScrapQuantity(wo.getTotalOperationScrapQuantity());
        dto.setFirstPassYield(wo.getFirstPassYield());
        dto.setReworkRate(wo.getReworkRate());
        dto.setScrapRate(wo.getScrapRate());
        dto.setOverallYield(wo.getOverallYield());
        return dto;
    }

    /**
     * On ACCEPT disposition, the rejected qty becomes good output. Move it from
     * rejectedQuantity to completedQuantity, forward to dependents (so downstream
     * ops see the additional good input), and refresh the WO completed total.
     */
    private void acceptRejectedQuantity(RejectionEntry rejection) {
        WorkOrderOperation op = rejection.getOperation();
        WorkOrder workOrder = rejection.getWorkOrder();
        BigDecimal qty = rejection.getRejectedQuantity();

        BigDecimal currentRejected = op.getRejectedQuantity() != null ? op.getRejectedQuantity() : BigDecimal.ZERO;
        BigDecimal currentCompleted = op.getCompletedQuantity() != null ? op.getCompletedQuantity() : BigDecimal.ZERO;

        op.setRejectedQuantity(currentRejected.subtract(qty).max(BigDecimal.ZERO));
        op.setCompletedQuantity(currentCompleted.add(qty));

        // If this brings the op up to or past plan, mark it COMPLETED.
        if (op.getStatus() != OperationStatus.COMPLETED
                && op.getCompletedQuantity().compareTo(op.getPlannedQuantity()) >= 0) {
            op.setStatus(OperationStatus.COMPLETED);
            op.setActualEndDate(new Date());
        }
        workOrderOperationRepository.save(op);

        // Forward the accepted qty to dependents (or next op in legacy sequential mode)
        forwardAcceptedToDependents(op, qty, workOrder);

        // Refresh WO completed total (= last operation's completedQuantity)
        BigDecimal woCompleted = workOrderOperationRepository.findByWorkOrder(workOrder).stream()
                .filter(o -> o.getDeletedDate() == null)
                .max(Comparator.comparingInt(WorkOrderOperation::getSequence))
                .map(o -> o.getCompletedQuantity() != null ? o.getCompletedQuantity() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
        workOrder.setCompletedQuantity(woCompleted);
        workOrderRepository.save(workOrder);
    }

    private void forwardAcceptedToDependents(WorkOrderOperation source, BigDecimal qty, WorkOrder workOrder) {
        List<WorkOrderOperation> dependents = workOrderOperationRepository.findByDependsOnOperationId(source.getId());
        if (!dependents.isEmpty()) {
            for (WorkOrderOperation dep : dependents) {
                BigDecimal current = dep.getAvailableInputQuantity() != null
                        ? dep.getAvailableInputQuantity() : BigDecimal.ZERO;
                dep.setAvailableInputQuantity(current.add(qty));
                workOrderOperationRepository.save(dep);
            }
        } else {
            // Legacy sequential mode — push to next-by-sequence op
            WorkOrderOperation nextOp = workOrderOperationRepository
                    .findTopByWorkOrderAndSequenceGreaterThanOrderBySequenceAsc(workOrder, source.getSequence());
            if (nextOp != null) {
                BigDecimal current = nextOp.getAvailableInputQuantity() != null
                        ? nextOp.getAvailableInputQuantity() : BigDecimal.ZERO;
                nextOp.setAvailableInputQuantity(current.add(qty));
                workOrderOperationRepository.save(nextOp);
            }
        }
    }

    private WorkOrder buildReworkWorkOrder(WorkOrder parent, BigDecimal qty) {
        Long seq = workOrderRepository.getNextWorkOrderSequence();

        WorkOrder rework = new WorkOrder();
        rework.setWorkOrderNumber("WO-" + seq);
        rework.setParentWorkOrder(parent);
        rework.setBom(parent.getBom());
        rework.setRouting(parent.getRouting());
        rework.setWorkCenter(parent.getWorkCenter());
        rework.setPlannedQuantity(qty);
        rework.setCompletedQuantity(BigDecimal.ZERO);
        rework.setScrappedQuantity(BigDecimal.ZERO);
        rework.setSourceType(WorkOrderSourceType.PARENT_WORK_ORDER);
        rework.setWorkOrderStatus(WorkOrderStatus.CREATED);
        rework.setRemarks("Rework from WO " + parent.getWorkOrderNumber());
        return rework;
    }

    private RejectionEntryDTO toDTO(RejectionEntry e) {
        RejectionEntryDTO dto = new RejectionEntryDTO();
        dto.setId(e.getId());
        dto.setWorkOrderId(e.getWorkOrder().getId());
        dto.setWorkOrderNumber(e.getWorkOrder().getWorkOrderNumber());
        dto.setOperationId(e.getOperation().getId());
        dto.setOperationName(e.getOperation().getOperationName());
        dto.setOperationSequence(e.getOperation().getSequence());
        dto.setRejectedQuantity(e.getRejectedQuantity());
        dto.setDispositionStatus(e.getDispositionStatus());
        dto.setDispositionReason(e.getDispositionReason());
        if (e.getChildWorkOrder() != null) {
            dto.setChildWorkOrderId(e.getChildWorkOrder().getId());
            dto.setChildWorkOrderNumber(e.getChildWorkOrder().getWorkOrderNumber());
        }
        dto.setCreatedAt(e.getCreatedAt());
        dto.setCreatedBy(e.getCreatedBy());
        dto.setDisposedAt(e.getDisposedAt());
        dto.setDisposedBy(e.getDisposedBy());
        return dto;
    }

    private String getCurrentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }
}
