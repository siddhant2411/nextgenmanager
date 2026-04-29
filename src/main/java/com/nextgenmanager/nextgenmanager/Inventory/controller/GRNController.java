package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.CreateGRNRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.GRNResponseDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryLedger;
import com.nextgenmanager.nextgenmanager.Inventory.service.GRNService;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/grn")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','INVENTORY_ADMIN','INVENTORY_USER','USER')")
public class GRNController {

    @Autowired private GRNService grnService;
    @Autowired private InventoryTransactionService inventoryTransactionService;

    @PostMapping
    public ResponseEntity<GRNResponseDTO> createGRN(@RequestBody CreateGRNRequest request) {
        return ResponseEntity.ok(grnService.createGRN(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GRNResponseDTO> getGRN(@PathVariable Long id) {
        return ResponseEntity.ok(grnService.getGRN(id));
    }

    @GetMapping
    public ResponseEntity<Page<GRNResponseDTO>> searchGRNs(
            @RequestParam(required = false) Long poId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String grnNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(grnService.searchGRNs(poId, status, vendorId, grnNumber, pageable));
    }

    @GetMapping("/by-po/{poId}")
    public ResponseEntity<List<GRNResponseDTO>> getGRNsByPO(@PathVariable Long poId) {
        return ResponseEntity.ok(grnService.getGRNsByPurchaseOrder(poId));
    }

    @GetMapping("/stock-history/{itemId}")
    public ResponseEntity<List<InventoryLedger>> getStockHistory(
            @PathVariable int itemId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(inventoryTransactionService.getStockHistory(itemId, from, to));
    }

    @GetMapping("/stock-value")
    public ResponseEntity<Double> getStockValue(@RequestParam(required = false) String warehouse) {
        return ResponseEntity.ok(inventoryTransactionService.getStockValue(warehouse));
    }
}
