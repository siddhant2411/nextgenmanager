package com.nextgenmanager.nextgenmanager.purchase.requisition.controller;

import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.*;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-requisitions")
@RequiresOperationsAccess
public class PurchaseRequisitionController {

    private final PurchaseRequisitionService service;

    public PurchaseRequisitionController(PurchaseRequisitionService service) {
        this.service = service;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<PurchaseRequisitionDto> create(@RequestBody PurchaseRequisitionCreateDto dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @GetMapping
    public ResponseEntity<Page<PurchaseRequisitionListDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String source,
            @PageableDefault(size = 20, sort = "createdDate") Pageable pageable) {
        return ResponseEntity.ok(service.list(status, approvalStatus, source, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseRequisitionDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseRequisitionDto> update(@PathVariable Long id,
                                                        @RequestBody PurchaseRequisitionUpdateDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @PostMapping("/{id}/submit")
    public ResponseEntity<PurchaseRequisitionDto> submit(@PathVariable Long id) {
        return ResponseEntity.ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    @RequiresPurchaseAdminAccess
    public ResponseEntity<PurchaseRequisitionDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @RequiresPurchaseAdminAccess
    public ResponseEntity<PurchaseRequisitionDto> reject(@PathVariable Long id,
                                                         @RequestBody PurchaseRequisitionApprovalActionDto dto) {
        return ResponseEntity.ok(service.reject(id, dto));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PurchaseRequisitionDto> cancel(@PathVariable Long id,
                                                         @RequestBody PurchaseRequisitionApprovalActionDto dto) {
        return ResponseEntity.ok(service.cancel(id, dto));
    }

    // ── Conversion ────────────────────────────────────────────────────────────

    @PostMapping("/{id}/convert-to-po")
    public ResponseEntity<PurchaseOrderDto> convertToPo(@PathVariable Long id,
                                                        @RequestBody ConvertToPoRequestDto dto) {
        return ResponseEntity.ok(service.convertToPurchaseOrder(id, dto));
    }

    @PostMapping("/from-work-order/{workOrderId}")
    public ResponseEntity<PurchaseRequisitionDto> generateFromWorkOrder(@PathVariable Long workOrderId) {
        return ResponseEntity.ok(service.generateFromWorkOrder(workOrderId));
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    @GetMapping("/next-number")
    public ResponseEntity<String> nextNumber() {
        return ResponseEntity.ok(service.nextNumber());
    }
}
