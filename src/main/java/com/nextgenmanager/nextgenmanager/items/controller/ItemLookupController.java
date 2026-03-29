package com.nextgenmanager.nextgenmanager.items.controller;

import com.nextgenmanager.nextgenmanager.items.model.MaterialType;
import com.nextgenmanager.nextgenmanager.items.model.ProcessType;
import com.nextgenmanager.nextgenmanager.items.repository.MaterialTypeRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ProcessTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Provides dropdown / autocomplete data for item specification fields:
 *   GET  /api/item-lookups/materials        → list of MaterialType
 *   POST /api/item-lookups/materials        → add new MaterialType
 *   GET  /api/item-lookups/process-types    → list of ProcessType
 *   POST /api/item-lookups/process-types    → add new ProcessType
 */
@RestController
@RequestMapping("/api/item-lookups")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER'," +
        "'ROLE_INVENTORY_ADMIN','ROLE_INVENTORY_USER','ROLE_SALES_ADMIN','ROLE_SALES_USER'," +
        "'ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
public class ItemLookupController {

    @Autowired
    private MaterialTypeRepository materialTypeRepo;

    @Autowired
    private ProcessTypeRepository processTypeRepo;

    // ── Material Types ────────────────────────────────────────────────────────

    @GetMapping("/materials")
    public List<MaterialType> getMaterials() {
        return materialTypeRepo.findByIsActiveTrueOrderByNameAsc();
    }

    @PostMapping("/materials")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_USER')")
    public ResponseEntity<MaterialType> addMaterial(@RequestBody MaterialType material) {
        material.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(materialTypeRepo.save(material));
    }

    // ── Process Types ─────────────────────────────────────────────────────────

    @GetMapping("/process-types")
    public List<ProcessType> getProcessTypes() {
        return processTypeRepo.findByIsActiveTrueOrderByNameAsc();
    }

    @PostMapping("/process-types")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_USER')")
    public ResponseEntity<ProcessType> addProcessType(@RequestBody ProcessType processType) {
        processType.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(processTypeRepo.save(processType));
    }
}
