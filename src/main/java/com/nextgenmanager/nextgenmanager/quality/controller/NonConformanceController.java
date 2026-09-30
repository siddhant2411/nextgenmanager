package com.nextgenmanager.nextgenmanager.quality.controller;

import com.nextgenmanager.nextgenmanager.quality.dto.NcrCreateRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.NcrDispositionRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.NonConformanceReportDto;
import com.nextgenmanager.nextgenmanager.quality.model.NcrStatus;
import com.nextgenmanager.nextgenmanager.quality.service.NonConformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Non-conformance reports: what was decided about goods that failed. */
@RestController
@RequestMapping("/api/non-conformance")
@RequiredArgsConstructor
public class NonConformanceController {

    private final NonConformanceService nonConformanceService;

    @GetMapping
    public ResponseEntity<List<NonConformanceReportDto>> list(
            @RequestParam(required = false) NcrStatus status) {
        return ResponseEntity.ok(nonConformanceService.list(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NonConformanceReportDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(nonConformanceService.get(id));
    }

    @GetMapping("/inspection-lot/{lotId}")
    public ResponseEntity<List<NonConformanceReportDto>> forLot(@PathVariable Long lotId) {
        return ResponseEntity.ok(nonConformanceService.forLot(lotId));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_QC','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<NonConformanceReportDto> raise(@RequestBody NcrCreateRequest request) {
        return ResponseEntity.ok(nonConformanceService.raise(request));
    }

    /** Deciding is kept to the roles that can answer for the decision, use-as-is most of all. */
    @PostMapping("/{id}/disposition")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    public ResponseEntity<NonConformanceReportDto> decide(@PathVariable Long id,
                                                          @RequestBody NcrDispositionRequest request) {
        return ResponseEntity.ok(nonConformanceService.decide(id, request));
    }
}
