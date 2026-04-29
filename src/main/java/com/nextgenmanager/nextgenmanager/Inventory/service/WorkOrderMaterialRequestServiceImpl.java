package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderMaterialReorderRepository;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class WorkOrderMaterialRequestServiceImpl implements WorkOrderMaterialRequestService {

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderMaterialRequestServiceImpl.class);

    @Autowired private InventoryRequestRepository inventoryRequestRepository;
    @Autowired private WorkOrderRepository workOrderRepository;
    @Autowired private WorkOrderMaterialRepository workOrderMaterialRepository;
    @Autowired private WorkOrderMaterialReorderRepository workOrderMaterialReorderRepository;
    @Autowired private InventoryTransactionService inventoryTransactionService;

    @Override
    @Transactional
    public InventoryRequest approveMaterialRequest(Long requestId, String approvedBy) {
        InventoryRequest mr = loadRequest(requestId);
        validatePendingOrPartial(mr);

        BigDecimal qty = mr.getRequestedQuantity();
        reserveStock(mr, qty);

        mr.setApprovalStatus(InventoryApprovalStatus.APPROVED);
        mr.setApprovedQuantity(qty);
        mr.setApprovedBy(approvedBy);
        mr.setApprovedDate(new Date());
        mr = inventoryRequestRepository.saveAndFlush(mr);

        syncWorkOrderStatus(mr.getSourceId());
        logger.info("MR {} fully approved by {} — reserved {}", requestId, approvedBy, qty);
        return mr;
    }

    @Override
    @Transactional
    public InventoryRequest partialApproveMaterialRequest(Long requestId, BigDecimal approvedQty, String approvedBy) {
        InventoryRequest mr = loadRequest(requestId);
        validatePendingOrPartial(mr);

        if (approvedQty == null || approvedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Approved quantity must be greater than zero");
        }
        if (approvedQty.compareTo(mr.getRequestedQuantity()) >= 0) {
            return approveMaterialRequest(requestId, approvedBy);
        }

        // If approved qty covers the net required quantity (actual need, excluding scrap buffer),
        // treat it as fully APPROVED — stores issued what production actually needs.
        // Reserve only approvedQty (not the full planned qty with scrap buffer).
        java.math.BigDecimal netRequired = workOrderMaterialRepository
                .findByInventoryRequestId(requestId)
                .map(WorkOrderMaterial::getNetRequiredQuantity)
                .orElse(null);
        boolean coversNetRequired = netRequired != null && approvedQty.compareTo(netRequired) >= 0;

        reserveStock(mr, approvedQty);

        if (coversNetRequired) {
            mr.setApprovalStatus(InventoryApprovalStatus.APPROVED);
            mr.setApprovedQuantity(approvedQty);
            mr.setApprovedBy(approvedBy);
            mr.setApprovedDate(new Date());
            mr = inventoryRequestRepository.saveAndFlush(mr);
            syncWorkOrderStatus(mr.getSourceId());
            logger.info("MR {} approved by {} — reserved {} (covers net required {})",
                    requestId, approvedBy, approvedQty, netRequired);
            return mr;
        }

        mr.setApprovalStatus(InventoryApprovalStatus.PARTIAL);
        mr.setApprovedQuantity(approvedQty);
        mr.setApprovedBy(approvedBy);
        mr.setApprovedDate(new Date());
        mr = inventoryRequestRepository.saveAndFlush(mr);

        syncWorkOrderStatus(mr.getSourceId());
        logger.info("MR {} partially approved by {} — reserved {} of {}", requestId, approvedBy, approvedQty, mr.getRequestedQuantity());
        return mr;
    }

    @Override
    @Transactional
    public InventoryRequest rejectMaterialRequest(Long requestId, String reason, String approvedBy) {
        InventoryRequest mr = loadRequest(requestId);
        validateForRejection(mr);

        // If stock was previously reserved (PARTIAL or APPROVED), release it back to available.
        if ((mr.getApprovalStatus() == InventoryApprovalStatus.PARTIAL
                || mr.getApprovalStatus() == InventoryApprovalStatus.APPROVED)
                && mr.getApprovedQuantity() != null
                && mr.getApprovedQuantity().compareTo(BigDecimal.ZERO) > 0) {
            InventoryTransactionDTO returnDto = new InventoryTransactionDTO();
            returnDto.setInventoryItemId(mr.getInventoryItem().getInventoryItemId());
            returnDto.setQuantity(mr.getApprovedQuantity().doubleValue());
            returnDto.setTransactionType("RETURN");
            returnDto.setReferenceDocNo(mr.getReferenceNumber());
            inventoryTransactionService.returnStock(returnDto);
            logger.info("MR {} {} approval reversed — returned {} units to available stock",
                    requestId, mr.getApprovalStatus(), mr.getApprovedQuantity());
        }

        mr.setApprovalStatus(InventoryApprovalStatus.REJECTED);
        mr.setApprovedQuantity(BigDecimal.ZERO);
        mr.setRejectionReason(reason);
        mr.setApprovedBy(approvedBy);
        mr.setApprovedDate(new Date());
        mr = inventoryRequestRepository.saveAndFlush(mr);

        syncWorkOrderStatus(mr.getSourceId());
        logger.info("MR {} rejected by {} — reason: {}", requestId, approvedBy, reason);
        return mr;
    }

    @Override
    public List<InventoryRequest> getMaterialRequestsForWorkOrder(Long workOrderId) {
        return inventoryRequestRepository.findBySourceIdAndRequestSource(workOrderId, InventoryRequestSource.WORK_ORDER);
    }

    @Override
    public Page<InventoryRequest> getPendingMaterialRequests(Pageable pageable) {
        return inventoryRequestRepository.findByRequestSourceAndApprovalStatusIn(
                InventoryRequestSource.WORK_ORDER,
                List.of(InventoryApprovalStatus.PENDING, InventoryApprovalStatus.PARTIAL),
                pageable
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InventoryRequest loadRequest(Long requestId) {
        return inventoryRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Material Request not found: " + requestId));
    }

    private void validatePendingOrPartial(InventoryRequest mr) {
        if (mr.getApprovalStatus() != InventoryApprovalStatus.PENDING
                && mr.getApprovalStatus() != InventoryApprovalStatus.PARTIAL) {
            throw new IllegalStateException("Material Request " + mr.getId()
                    + " is already " + mr.getApprovalStatus() + " and cannot be modified");
        }
    }

    private void validateForRejection(InventoryRequest mr) {
        if (mr.getApprovalStatus() == InventoryApprovalStatus.REJECTED) {
            throw new IllegalStateException("Material Request " + mr.getId() + " is already REJECTED");
        }
    }

    private void reserveStock(InventoryRequest mr, BigDecimal qty) {
        InventoryTransactionDTO dto = new InventoryTransactionDTO();
        dto.setInventoryItemId(mr.getInventoryItem().getInventoryItemId());
        dto.setQuantity(qty.doubleValue());
        dto.setTransactionType("RESERVE");
        dto.setReferenceDocNo(mr.getReferenceNumber());
        inventoryTransactionService.reserveStock(dto);
    }

    /**
     * Recalculates WO status after any MR action:
     *   All APPROVED              → READY_FOR_PRODUCTION
     *   At least one APPROVED/PARTIAL, rest anything → PARTIALLY_READY
     *   All REJECTED              → HOLD
     *   Otherwise                 → stays MATERIAL_PENDING
     */
    @Override
    @Transactional
    public void syncWorkOrderStatus(Long workOrderId) {
        if (workOrderId == null) {
            logger.warn("updateWorkOrderStatus called with null workOrderId — skipping");
            return;
        }
        WorkOrder wo = workOrderRepository.findById(workOrderId.intValue())
                .orElse(null);
        if (wo == null) {
            logger.warn("updateWorkOrderStatus: WorkOrder id={} not found — skipping", workOrderId);
            return;
        }

        WorkOrderStatus current = wo.getWorkOrderStatus();

        // MATERIAL_REORDER: revert to IN_PROGRESS once no reorder MRs remain pending
        if (current == WorkOrderStatus.MATERIAL_REORDER) {
            List<Long> reorderMrIds = workOrderMaterialReorderRepository
                    .findInventoryRequestIdsByWorkOrderId(workOrderId.intValue());
            boolean anyPending = inventoryRequestRepository.findAllById(reorderMrIds)
                    .stream()
                    .anyMatch(r -> r.getApprovalStatus() == InventoryApprovalStatus.PENDING
                            || r.getApprovalStatus() == InventoryApprovalStatus.PARTIAL);
            if (!anyPending) {
                wo.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);
                workOrderRepository.saveAndFlush(wo);
                logger.info("WO {} all reorder MRs settled — reverted to IN_PROGRESS", wo.getWorkOrderNumber());
            }
            return;
        }

        // Only recompute status while WO is still in an approval-gated state.
        // Once the WO has moved into IN_PROGRESS/COMPLETED/etc., MR actions should not reset it.
        if (current != WorkOrderStatus.MATERIAL_PENDING
                && current != WorkOrderStatus.PARTIALLY_READY
                && current != WorkOrderStatus.READY_FOR_PRODUCTION
                && current != WorkOrderStatus.HOLD) {
            logger.info("WO {} in status {} — MR action will not change WO status", wo.getWorkOrderNumber(), current);
            return;
        }

        List<InventoryRequest> allMrs = inventoryRequestRepository
                .findBySourceIdAndRequestSource(workOrderId, InventoryRequestSource.WORK_ORDER);

        if (allMrs.isEmpty()) {
            logger.warn("updateWorkOrderStatus: No MRs found for WO id={} — leaving status unchanged", workOrderId);
            return;
        }

        long approved = allMrs.stream().filter(r -> r.getApprovalStatus() == InventoryApprovalStatus.APPROVED).count();
        long partial  = allMrs.stream().filter(r -> r.getApprovalStatus() == InventoryApprovalStatus.PARTIAL).count();
        long rejected = allMrs.stream().filter(r -> r.getApprovalStatus() == InventoryApprovalStatus.REJECTED).count();
        long total    = allMrs.size();

        WorkOrderStatus newStatus;
        if (approved == total) {
            newStatus = WorkOrderStatus.READY_FOR_PRODUCTION;
        } else if (approved + partial > 0) {
            newStatus = WorkOrderStatus.PARTIALLY_READY;
        } else if (rejected == total) {
            newStatus = WorkOrderStatus.HOLD;
        } else {
            newStatus = WorkOrderStatus.MATERIAL_PENDING;
        }

        logger.info("WO {} MR summary: total={}, approved={}, partial={}, rejected={} → {}",
                wo.getWorkOrderNumber(), total, approved, partial, rejected, newStatus);

        if (current != newStatus) {
            wo.setWorkOrderStatus(newStatus);
            workOrderRepository.saveAndFlush(wo);
            logger.info("WO {} status transitioned {} → {}", wo.getWorkOrderNumber(), current, newStatus);
        }
    }
}
