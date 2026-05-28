package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.CreateGRNRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.GRNResponseDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryLedger;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.GRNService;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/grn")
@RequiresPurchaseAccess
public class GRNController {

    @Autowired private GRNService grnService;
    @Autowired private InventoryTransactionService inventoryTransactionService;
    @Autowired private InventoryLedgerRepository inventoryLedgerRepository;

    @PostMapping
    @RequiresPurchaseInventoryAdminAccess
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

    /**
     * Returns the closing balance of an item just before the given date.
     * Used by the frontend to show "Opening Balance as on [date]" in the stock ledger report.
     *
     * Example: GET /api/grn/stock-balance/42?asOf=2025-04-01
     * → returns the qty on hand at end-of-day 31-Mar-2025
     */
    @GetMapping("/stock-balance/{itemId}")
    public ResponseEntity<Double> getOpeningBalance(
            @PathVariable int itemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(inventoryTransactionService.getOpeningBalance(itemId, asOf));
    }

    @GetMapping("/stock-value")
    public ResponseEntity<Double> getStockValue(@RequestParam(required = false) String warehouse) {
        return ResponseEntity.ok(inventoryTransactionService.getStockValue(warehouse));
    }

    /**
     * Inward / Outward register — date-wise list of all stock movements across all items.
     * Needed for GST audit. Data is sourced entirely from InventoryLedger.
     *
     * GET /api/grn/register?from=2025-01-01&to=2025-03-31
     * GET /api/grn/register?from=2025-01-01&to=2025-03-31&type=INWARD
     * GET /api/grn/register?from=2025-01-01&to=2025-03-31&type=OUTWARD
     *
     * type = INWARD  → GRN / PRODUCE / RETURN / ADJUSTMENT(+)
     * type = OUTWARD → CONSUME / RESERVE / SALES_DISPATCH / ADJUSTMENT(−)
     * type omitted   → all movements
     */
    @GetMapping("/register")
    public ResponseEntity<Page<InventoryLedger>> getRegister(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(inventoryLedgerRepository.findRegister(from, to, type, pageable));
    }
}
