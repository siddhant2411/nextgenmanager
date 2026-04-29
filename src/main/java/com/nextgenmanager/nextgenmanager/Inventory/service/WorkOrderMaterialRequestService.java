package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface WorkOrderMaterialRequestService {

    /** Full approval — reserves the full requestedQuantity. */
    InventoryRequest approveMaterialRequest(Long requestId, String approvedBy);

    /** Partial approval — reserves approvedQty; remainder stays PENDING until re-processed or manually resolved. */
    InventoryRequest partialApproveMaterialRequest(Long requestId, BigDecimal approvedQty, String approvedBy);

    /** Rejection — no stock change; records reason. */
    InventoryRequest rejectMaterialRequest(Long requestId, String reason, String approvedBy);

    /** Returns all pending/partial MRs for a given Work Order. */
    List<InventoryRequest> getMaterialRequestsForWorkOrder(Long workOrderId);

    /** Paginated view of all pending WO material requests (Stores dashboard). */
    Page<InventoryRequest> getPendingMaterialRequests(Pageable pageable);

    /** Manually recalculate and sync Work Order status from MR state */
    void syncWorkOrderStatus(Long workOrderId);
}
