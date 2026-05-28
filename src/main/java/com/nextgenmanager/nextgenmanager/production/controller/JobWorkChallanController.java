package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.production.dto.JobWorkChallanDTO;
import com.nextgenmanager.nextgenmanager.production.dto.JobWorkChallanReceiptDTO;
import com.nextgenmanager.nextgenmanager.production.dto.JobWorkChallanRequestDTO;
import com.nextgenmanager.nextgenmanager.production.enums.ChallanStatus;
import com.nextgenmanager.nextgenmanager.production.service.jobwork.JobWorkChallanExportService;
import com.nextgenmanager.nextgenmanager.production.service.jobwork.JobWorkChallanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresProductionAdminAccess;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-work-challans")
@Tag(name = "Job Work Challan", description = "GST Rule 45 / Section 143 — dispatch & receipt of materials sent to job workers")
public class JobWorkChallanController {

    @Autowired private JobWorkChallanService service;
    @Autowired private JobWorkChallanExportService exportService;

    @PostMapping
    @RequiresProductionAdminAccess
    @Operation(summary = "Create a new Job Work Challan in DRAFT status")
    public ResponseEntity<JobWorkChallanDTO> create(@Valid @RequestBody JobWorkChallanRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @GetMapping
    @RequiresProductionAdminAccess
    @Operation(summary = "List all challans (optionally filter by status, vendorId, workOrderId)")
    public ResponseEntity<List<JobWorkChallanDTO>> getAll(
            @RequestParam(required = false) ChallanStatus status,
            @RequestParam(required = false) Integer vendorId,
            @RequestParam(required = false) Long workOrderId) {

        if (status != null) return ResponseEntity.ok(service.getByStatus(status));
        if (vendorId != null) return ResponseEntity.ok(service.getByVendor(vendorId));
        if (workOrderId != null) return ResponseEntity.ok(service.getByWorkOrder(workOrderId));
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/overdue")
    @RequiresProductionAdminAccess
    @Operation(
        summary = "List overdue challans",
        description = "Returns DISPATCHED / PARTIALLY_RECEIVED challans past the 180-day GST return deadline."
    )
    public ResponseEntity<List<JobWorkChallanDTO>> getOverdue() {
        return ResponseEntity.ok(service.getOverdue());
    }

    @GetMapping("/{id}")
    @RequiresProductionAdminAccess
    public ResponseEntity<JobWorkChallanDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    @RequiresProductionAdminAccess
    @Operation(summary = "Update a DRAFT challan (lines, vendor, remarks)")
    public ResponseEntity<JobWorkChallanDTO> update(
            @PathVariable Long id, @Valid @RequestBody JobWorkChallanRequestDTO req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @PostMapping("/{id}/dispatch")
    @RequiresProductionAdminAccess
    @Operation(
        summary = "Dispatch challan — DRAFT → DISPATCHED",
        description = "Sets dispatch date and starts the 180-day GST return clock."
    )
    public ResponseEntity<JobWorkChallanDTO> dispatch(@PathVariable Long id) {
        return ResponseEntity.ok(service.dispatch(id));
    }

    @PostMapping("/{id}/receive")
    @RequiresProductionAdminAccess
    @Operation(
        summary = "Record receipt of materials from job worker",
        description = "Updates line quantities. Challan auto-transitions to PARTIALLY_RECEIVED or COMPLETED."
    )
    public ResponseEntity<JobWorkChallanDTO> receiveBack(
            @PathVariable Long id, @Valid @RequestBody JobWorkChallanReceiptDTO receipt) {
        return ResponseEntity.ok(service.receiveBack(id, receipt));
    }

    @PostMapping("/{id}/cancel")
    @RequiresProductionAdminAccess
    @Operation(summary = "Cancel a DRAFT or DISPATCHED challan")
    public ResponseEntity<JobWorkChallanDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    @DeleteMapping("/{id}")
    @RequiresProductionAdminAccess
    @Operation(summary = "Soft-delete a DRAFT challan")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/print")
    @RequiresProductionAdminAccess
    @Operation(summary = "Download Job Work Challan as PDF")
    public ResponseEntity<byte[]> printChallan(@PathVariable Long id) {
        try {
            byte[] pdf = exportService.generateChallanPdf(id);
            JobWorkChallanDTO challan = service.getById(id);
            String filename = "JobWorkChallan_" + challan.getChallanNumber().replace("/", "-") + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
