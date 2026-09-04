package com.nextgenmanager.nextgenmanager.quality.controller;

import com.nextgenmanager.nextgenmanager.quality.dto.InspectionJudgeRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.InspectionLotCreateRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.InspectionLotDto;
import com.nextgenmanager.nextgenmanager.quality.dto.InspectionWaiverRequest;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionSource;
import com.nextgenmanager.nextgenmanager.quality.service.InspectionLotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Inspections and their verdicts.
 *
 * <p>Raising and judging are separate calls because they are separate events, often hours apart
 * and by different people: the lot exists from the moment somebody says these goods are to be
 * looked at, and it blocks what it gates until the answer arrives.
 */
@RestController
@RequestMapping("/api/inspection-lot")
@RequiredArgsConstructor
public class InspectionLotController {

    private final InspectionLotService inspectionLotService;

    @GetMapping
    public ResponseEntity<List<InspectionLotDto>> list(
            @RequestParam(required = false) InspectionSource source,
            @RequestParam(required = false) InspectionLotStatus status) {
        return ResponseEntity.ok(inspectionLotService.list(source, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InspectionLotDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(inspectionLotService.get(id));
    }

    @GetMapping("/work-order/{workOrderId}")
    public ResponseEntity<List<InspectionLotDto>> forWorkOrder(@PathVariable int workOrderId) {
        return ResponseEntity.ok(inspectionLotService.forWorkOrder(workOrderId));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_QC','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<InspectionLotDto> raise(@RequestBody InspectionLotCreateRequest request) {
        return ResponseEntity.ok(inspectionLotService.raise(request));
    }

    @PostMapping("/{id}/judge")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_QC')")
    public ResponseEntity<InspectionLotDto> judge(@PathVariable Long id,
                                                  @RequestBody InspectionJudgeRequest request) {
        return ResponseEntity.ok(inspectionLotService.judge(id, request));
    }

    /**
     * Releasing goods that failed. Kept to the roles that can answer for it — a waiver is the one
     * action here that overrides an inspection rather than recording one.
     */
    @PostMapping("/{id}/waive")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<InspectionLotDto> waive(@PathVariable Long id,
                                                  @RequestBody InspectionWaiverRequest request) {
        return ResponseEntity.ok(inspectionLotService.waive(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_QC')")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        inspectionLotService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
