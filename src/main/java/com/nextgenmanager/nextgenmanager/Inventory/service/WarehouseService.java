package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.StorageLocationDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.WarehouseDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.WarehouseStockRowDto;
import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;

import java.util.List;

public interface WarehouseService {

    List<WarehouseDto> listWarehouses(boolean activeOnly);

    WarehouseDto getWarehouse(Long id);

    WarehouseDto createWarehouse(WarehouseDto dto);

    WarehouseDto updateWarehouse(Long id, WarehouseDto dto);

    /**
     * Soft-deletes a warehouse. Refuses while it is the default or still holds locations,
     * so nothing ends up pointing at a warehouse that no longer exists.
     */
    void deleteWarehouse(Long id);

    /**
     * The warehouse everything falls back to when no other is named. Resolved through this
     * one method so callers never have to guess, and never hard-code a code like "MAIN".
     */
    Warehouse resolveDefaultWarehouse();

    /**
     * Resolves an incoming warehouse code to a real warehouse.
     *
     * <p>Blank or absent means "wherever things go by default" and yields the default warehouse.
     * A code that names nothing throws rather than quietly falling back — a typo silently
     * dumping stock into the main store is exactly the class of error the free-text column
     * used to allow.
     */
    Warehouse resolveByCodeOrDefault(String code);

    /** What this warehouse is holding, item by item. Items at zero have no row. */
    List<WarehouseStockRowDto> listStock(Long warehouseId);

    List<StorageLocationDto> listLocations(Long warehouseId, boolean pickableOnly);

    StorageLocationDto createLocation(Long warehouseId, StorageLocationDto dto);

    StorageLocationDto updateLocation(Long locationId, StorageLocationDto dto);

    void deleteLocation(Long locationId);
}
