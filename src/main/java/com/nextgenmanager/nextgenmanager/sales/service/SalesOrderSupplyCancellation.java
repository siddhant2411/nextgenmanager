package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.workorder.WorkOrderService;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionApprovalActionDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisition;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionSource;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.repository.PurchaseRequisitionRepository;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionService;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What becomes of the supply an order set in motion when the order is cancelled.
 *
 * <p>Approving a make-to-order sales order raises work orders and purchase requisitions for what
 * stock could not cover. Cancelling the order used to leave every one of them standing: a shop
 * floor making, and a buyer buying, for a customer who is no longer waiting.
 *
 * <p>Only supply nobody has acted on is cancelled. A work order that has started has material on
 * the floor and labour booked against it, and what it makes can be sold to somebody else; a
 * requisition that became a purchase order is a commitment to a vendor. Those are left alone and
 * named, so that a person decides.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderSupplyCancellation {

    private static final Logger logger = LoggerFactory.getLogger(SalesOrderSupplyCancellation.class);

    /** Nothing has been made yet: at most, material has been reserved, and cancelling returns it. */
    private static final Set<WorkOrderStatus> NOT_STARTED = EnumSet.of(
            WorkOrderStatus.CREATED, WorkOrderStatus.SCHEDULED, WorkOrderStatus.MATERIAL_PENDING,
            WorkOrderStatus.READY_FOR_PRODUCTION, WorkOrderStatus.PARTIALLY_READY,
            WorkOrderStatus.HOLD);

    private static final Set<WorkOrderStatus> FINISHED = EnumSet.of(
            WorkOrderStatus.COMPLETED, WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED,
            WorkOrderStatus.SHORT_CLOSED);

    private final WorkOrderRepository workOrderRepository;
    private final PurchaseRequisitionRepository purchaseRequisitionRepository;
    // Looked up on use: both services sit in modules that reach back into sales, and a direct
    // constructor dependency here would close that loop at start-up.
    private final ObjectProvider<WorkOrderService> workOrderService;
    private final ObjectProvider<PurchaseRequisitionService> purchaseRequisitionService;

    /** What was done, and what was left for a person. */
    public record Outcome(List<String> cancelled, List<String> leftRunning) {
        public boolean isEmpty() {
            return cancelled.isEmpty() && leftRunning.isEmpty();
        }

        /** One line for the order's record, or null when the order had set nothing in motion. */
        public String summary() {
            if (isEmpty()) return null;
            List<String> parts = new ArrayList<>();
            if (!cancelled.isEmpty()) parts.add("Cancelled with the order: " + String.join(", ", cancelled));
            if (!leftRunning.isEmpty()) parts.add("Still open, review: " + String.join(", ", leftRunning));
            return String.join(". ", parts) + ".";
        }
    }

    public Outcome cancelUnstartedSupply(SalesOrder so) {
        List<String> cancelled = new ArrayList<>();
        List<String> leftRunning = new ArrayList<>();

        for (WorkOrder wo : workOrderRepository.findLiveBySalesOrder(so.getId())) {
            WorkOrderStatus status = wo.getWorkOrderStatus();
            if (status == null || FINISHED.contains(status)) continue;
            if (!NOT_STARTED.contains(status)) {
                leftRunning.add(wo.getWorkOrderNumber() + " (" + status + ")");
                continue;
            }
            // Not caught: both calls join this transaction, and an exception thrown through
            // their proxies marks it rollback-only. Swallowing one would let the order appear to
            // cancel and then fail at commit with nothing to say why.
            workOrderService.getObject().cancelWorkOrder(wo.getId());
            cancelled.add(wo.getWorkOrderNumber());
        }

        for (PurchaseRequisition pr : purchaseRequisitionRepository
                .findBySourceAndSourceRefIdAndDeletedDateIsNull(PurchaseRequisitionSource.SALES_ORDER, so.getId())) {
            if (pr.getStatus() == PurchaseRequisitionStatus.CANCELLED) continue;
            if (pr.getStatus() == PurchaseRequisitionStatus.CLOSED) {
                leftRunning.add(pr.getPrNumber() + " (already ordered)");
                continue;
            }
            purchaseRequisitionService.getObject().cancel(pr.getId(),
                    new PurchaseRequisitionApprovalActionDto(
                            "Sales order " + so.getOrderNumber() + " was cancelled"));
            cancelled.add(pr.getPrNumber());
        }

        if (!cancelled.isEmpty() || !leftRunning.isEmpty()) {
            logger.info("SO {} cancel: cancelled {}, left {}", so.getOrderNumber(), cancelled, leftRunning);
        }
        return new Outcome(cancelled, leftRunning);
    }
}
