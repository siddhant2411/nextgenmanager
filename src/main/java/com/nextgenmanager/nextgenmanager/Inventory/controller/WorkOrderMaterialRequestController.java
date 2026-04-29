package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.service.WorkOrderMaterialRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/material-requests")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_INVENTORY_USER')")
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

    /** Full approval. */
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<InventoryRequest> approve(
            @PathVariable Long requestId,
            Authentication auth) {
        return ResponseEntity.ok(mrService.approveMaterialRequest(requestId, auth.getName()));
    }

    /** Partial approval — body: { "approvedQuantity": 40.0 } */
    @PostMapping("/{requestId}/partial-approve")
    public ResponseEntity<InventoryRequest> partialApprove(
            @PathVariable Long requestId,
            @RequestBody Map<String, BigDecimal> body,
            Authentication auth) {
        BigDecimal qty = body.get("approvedQuantity");
        if (qty == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(mrService.partialApproveMaterialRequest(requestId, qty, auth.getName()));
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
