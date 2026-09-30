package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferCreateRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferReceiveRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.StockTransferStatus;
import com.nextgenmanager.nextgenmanager.Inventory.service.StockTransferService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresInventoryAdminAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Stock transfers between warehouses. Dispatch and receive are separate calls because they are
 * separate events — the stock is in transit in between, and pretending otherwise would make the
 * per-warehouse figures wrong for as long as the goods are moving.
 */
@RestController
@RequestMapping("/api/stock-transfer")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @GetMapping
    public ResponseEntity<List<StockTransferDto>> list(
            @RequestParam(required = false) StockTransferStatus status,
            @RequestParam(required = false) Long warehouseId) {
        return ResponseEntity.ok(stockTransferService.list(status, warehouseId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockTransferDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(stockTransferService.get(id));
    }

    @PostMapping
    @RequiresInventoryAdminAccess
    public ResponseEntity<StockTransferDto> create(@RequestBody StockTransferCreateRequest request) {
        return ResponseEntity.ok(stockTransferService.create(request));
    }

    @PostMapping("/{id}/dispatch")
    @RequiresInventoryAdminAccess
    public ResponseEntity<StockTransferDto> dispatch(@PathVariable Long id) {
        return ResponseEntity.ok(stockTransferService.dispatch(id));
    }

    @PostMapping("/{id}/receive")
    @RequiresInventoryAdminAccess
    public ResponseEntity<StockTransferDto> receive(
            @PathVariable Long id,
            @RequestBody(required = false) StockTransferReceiveRequest request) {
        return ResponseEntity.ok(stockTransferService.receive(id, request));
    }

    @PostMapping("/{id}/cancel")
    @RequiresInventoryAdminAccess
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        stockTransferService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
