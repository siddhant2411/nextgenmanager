package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.StorageLocationDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.WarehouseDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.WarehouseStockRowDto;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresInventoryAdminAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Warehouse master — the places stock can sit, and the bins inside them.
 *
 * <p>Reads are open to anyone with inventory access because pickers and planners need the list;
 * writes are admin-only, since changing the default warehouse or retiring one moves where
 * everything downstream points.
 */
@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    public ResponseEntity<List<WarehouseDto>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(warehouseService.listWarehouses(activeOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(warehouseService.getWarehouse(id));
    }

    @PostMapping
    @RequiresInventoryAdminAccess
    public ResponseEntity<WarehouseDto> create(@RequestBody WarehouseDto dto) {
        return ResponseEntity.ok(warehouseService.createWarehouse(dto));
    }

    @PutMapping("/{id}")
    @RequiresInventoryAdminAccess
    public ResponseEntity<WarehouseDto> update(@PathVariable Long id, @RequestBody WarehouseDto dto) {
        return ResponseEntity.ok(warehouseService.updateWarehouse(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequiresInventoryAdminAccess
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ResponseEntity.noContent().build();
    }

    /** What this warehouse is holding. The question the whole phase exists to answer. */
    @GetMapping("/{warehouseId}/stock")
    public ResponseEntity<List<WarehouseStockRowDto>> stock(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(warehouseService.listStock(warehouseId));
    }

    // ─── Locations ────────────────────────────────────────────────────────────

    @GetMapping("/{warehouseId}/locations")
    public ResponseEntity<List<StorageLocationDto>> listLocations(
            @PathVariable Long warehouseId,
            @RequestParam(defaultValue = "false") boolean pickableOnly) {
        return ResponseEntity.ok(warehouseService.listLocations(warehouseId, pickableOnly));
    }

    @PostMapping("/{warehouseId}/locations")
    @RequiresInventoryAdminAccess
    public ResponseEntity<StorageLocationDto> createLocation(
            @PathVariable Long warehouseId, @RequestBody StorageLocationDto dto) {
        return ResponseEntity.ok(warehouseService.createLocation(warehouseId, dto));
    }

    @PutMapping("/locations/{locationId}")
    @RequiresInventoryAdminAccess
    public ResponseEntity<StorageLocationDto> updateLocation(
            @PathVariable Long locationId, @RequestBody StorageLocationDto dto) {
        return ResponseEntity.ok(warehouseService.updateLocation(locationId, dto));
    }

    @DeleteMapping("/locations/{locationId}")
    @RequiresInventoryAdminAccess
    public ResponseEntity<Void> deleteLocation(@PathVariable Long locationId) {
        warehouseService.deleteLocation(locationId);
        return ResponseEntity.noContent().build();
    }
}
