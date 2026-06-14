package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.PlanningDeskRowDto;
import com.nextgenmanager.nextgenmanager.Inventory.service.ProcurementPlanningService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresPlanningAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Procurement Planning Desk — make/buy decisions on needs that could not be auto-routed.
 * Owned by ROLE_PLANNER (and admins); the store does not decide procurement method here.
 */
@RestController
@RequestMapping("/api/procurement-planning")
@RequiresPlanningAccess
@RequiredArgsConstructor
public class ProcurementPlanningController {

    private final ProcurementPlanningService planningService;

    /** Needs awaiting a decision, each with its make/buy recommendation. */
    @GetMapping("/queue")
    public ResponseEntity<List<PlanningDeskRowDto>> queue() {
        return ResponseEntity.ok(planningService.getQueue());
    }

    /** Planner routes the need to a (draft) Work Order. Returns the new work order id. */
    @PostMapping("/{procurementOrderId}/make")
    public ResponseEntity<Map<String, Object>> make(@PathVariable Long procurementOrderId) {
        Long workOrderId = planningService.decideMake(procurementOrderId);
        return ResponseEntity.ok(Map.of("decision", "WORK_ORDER", "workOrderId", workOrderId));
    }

    /** Planner routes the need to a (draft) Purchase Requisition. Returns the new requisition id. */
    @PostMapping("/{procurementOrderId}/buy")
    public ResponseEntity<Map<String, Object>> buy(@PathVariable Long procurementOrderId) {
        Long requisitionId = planningService.decideBuy(procurementOrderId);
        return ResponseEntity.ok(Map.of("decision", "PURCHASE_ORDER", "purchaseRequisitionId", requisitionId));
    }

    /** Planner declines the auto-raised need (will be covered by stock / reorder). */
    @PostMapping("/{procurementOrderId}/defer")
    public ResponseEntity<Void> defer(@PathVariable Long procurementOrderId) {
        planningService.defer(procurementOrderId);
        return ResponseEntity.ok().build();
    }
}
