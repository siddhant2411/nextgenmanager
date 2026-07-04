package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.service.WorkOrderMaterialRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresInventoryAccess;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/material-requests")
@RequiresInventoryAccess
public class WorkOrderMaterialRequestController {

    @Autowired
    private WorkOrderMaterialRequestService mrService;

    /** All MRs for a specific Work Order (production view). */
    @GetMapping("/work-order/{workOrderId}")
    public ResponseEntity<List<InventoryRequest>> getByWorkOrder(@PathVariable Long workOrderId) {
        return ResponseEntity.ok(mrService.getMaterialRequestsForWorkOrder(workOrderId));
    }

    /** Helper to forcibly resync WO status using the MRs currently attached */
    @PostMapping("/work-order/{workOrderId}/sync-status")
    public ResponseEntity<Void> forceSyncWOStatus(@PathVariable Long workOrderId) {
        mrService.syncWorkOrderStatus(workOrderId);
        return ResponseEntity.ok().build();
    }

    /** Paginated list of pending/partial MRs (Stores dashboard). */
    @GetMapping("/pending")
    public ResponseEntity<Page<InventoryRequest>> getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("requestedDate").ascending());
        return ResponseEntity.ok(mrService.getPendingMaterialRequests(pageable));
    }

    /**
     * Full approval. When approving would drive available stock negative (non batch/serial-tracked
     * items), the call responds 409 {@code requiresConfirmation} unless {@code force=true} is passed.
     */
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<InventoryRequest> approve(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "false") boolean force,
            Authentication auth) {
        return ResponseEntity.ok(mrService.approveMaterialRequest(requestId, auth.getName(), force));
    }

    /** Partial approval — body: { "approvedQuantity": 40.0 }; {@code force} confirms a negative-stock shortfall. */
    @PostMapping("/{requestId}/partial-approve")
    public ResponseEntity<InventoryRequest> partialApprove(
            @PathVariable Long requestId,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestBody Map<String, BigDecimal> body,
            Authentication auth) {
        BigDecimal qty = body.get("approvedQuantity");
        if (qty == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(mrService.partialApproveMaterialRequest(requestId, qty, auth.getName(), force));
    }

    /** Rejection — body: { "reason": "Out of Stock" } */
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<InventoryRequest> reject(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String reason = body.getOrDefault("reason", "Rejected by store keeper");
        return ResponseEntity.ok(mrService.rejectMaterialRequest(requestId, reason, auth.getName()));
    }
}
