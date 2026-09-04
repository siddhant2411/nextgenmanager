package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.StorageLocationDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.WarehouseDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.WarehouseStockRowDto;
import com.nextgenmanager.nextgenmanager.Inventory.repository.ItemWarehouseStockRepository;
import com.nextgenmanager.nextgenmanager.Inventory.model.StorageLocation;
import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.model.WarehouseType;
import com.nextgenmanager.nextgenmanager.Inventory.repository.StorageLocationRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private static final Logger logger = LoggerFactory.getLogger(WarehouseServiceImpl.class);

    private final WarehouseRepository warehouseRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final ItemWarehouseStockRepository itemWarehouseStockRepository;

    // ─── Warehouses ───────────────────────────────────────────────────────────

    @Override
    public List<WarehouseDto> listWarehouses(boolean activeOnly) {
        List<Warehouse> rows = activeOnly ? warehouseRepository.findAllActive() : warehouseRepository.findAllLive();
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    public WarehouseDto getWarehouse(Long id) {
        return toDto(loadWarehouse(id));
    }

    @Override
    @Transactional
    public WarehouseDto createWarehouse(WarehouseDto dto) {
        String code = requireCode(dto.code());
        warehouseRepository.findLiveByCode(code).ifPresent(existing -> {
            throw new IllegalArgumentException("A warehouse with code " + code + " already exists");
        });

        // A partial unique index enforces one default. The incumbent must be cleared AND flushed
        // before this row is written, or both rows carry isDefault=true at flush time and the
        // constraint fires on a change the caller asked for legitimately.
        if (dto.isDefault()) {
            clearOtherDefaults(null);
            warehouseRepository.flush();
        }

        Warehouse w = new Warehouse();
        w.setCode(code);
        apply(w, dto);

        Warehouse saved = warehouseRepository.save(w);
        logger.info("Warehouse created: {} ({})", saved.getCode(), saved.getWarehouseType());
        return toDto(saved);
    }

    @Override
    @Transactional
    public WarehouseDto updateWarehouse(Long id, WarehouseDto dto) {
        Warehouse w = loadWarehouse(id);

        if (dto.code() != null && !dto.code().isBlank() && !dto.code().equalsIgnoreCase(w.getCode())) {
            String code = requireCode(dto.code());
            warehouseRepository.findLiveByCode(code).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new IllegalArgumentException("A warehouse with code " + code + " already exists");
                }
            });
            w.setCode(code);
        }

        boolean wasDefault = w.isDefault();
        boolean wantsDefault = dto.isDefault();

        // Checked before anything is mutated: refusing here beats leaving the system with no
        // fallback warehouse at all.
        if (!wantsDefault && wasDefault) {
            throw new IllegalStateException(
                    "Cannot unset the default warehouse directly. Make another warehouse the default instead.");
        }

        // Same ordering rule as create: the incumbent has to be cleared and flushed before this
        // row is marked default, otherwise two rows are default at flush time and the partial
        // unique index rejects a legitimate change.
        if (wantsDefault && !wasDefault) {
            clearOtherDefaults(id);
            warehouseRepository.flush();
        }

        apply(w, dto);
        w.setUpdatedDate(new Date());
        return toDto(warehouseRepository.save(w));
    }

    @Override
    @Transactional
    public void deleteWarehouse(Long id) {
        Warehouse w = loadWarehouse(id);

        if (w.isDefault()) {
            throw new IllegalStateException(
                    "Cannot delete the default warehouse. Make another warehouse the default first.");
        }
        long locations = storageLocationRepository.countLiveByWarehouse(id);
        if (locations > 0) {
            throw new IllegalStateException(
                    "Cannot delete warehouse " + w.getCode() + ": it still has " + locations + " location(s)");
        }

        w.setDeletedDate(new Date());
        warehouseRepository.save(w);
        logger.info("Warehouse soft-deleted: {}", w.getCode());
    }

    @Override
    public Warehouse resolveByCodeOrDefault(String code) {
        if (code == null || code.isBlank()) return resolveDefaultWarehouse();
        return warehouseRepository.findLiveByCode(code.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No warehouse with code " + code.trim()
                                + ". Leave it blank to use the default warehouse."));
    }

    @Override
    public Warehouse resolveDefaultWarehouse() {
        return warehouseRepository.findDefault().orElseThrow(() -> new IllegalStateException(
                "No default warehouse is configured. Mark one warehouse as the default."));
    }

    @Override
    public List<WarehouseStockRowDto> listStock(Long warehouseId) {
        Warehouse w = loadWarehouse(warehouseId);
        return itemWarehouseStockRepository.findByWarehouse(warehouseId).stream()
                .map(r -> new WarehouseStockRowDto(
                        r.getInventoryItem().getInventoryItemId(),
                        r.getInventoryItem().getItemCode(),
                        r.getInventoryItem().getName(),
                        w.getCode(),
                        r.getOnHand().doubleValue(),
                        r.getReserved().doubleValue(),
                        r.getInTransit().doubleValue()))
                .toList();
    }

    // ─── Locations ────────────────────────────────────────────────────────────

    @Override
    public List<StorageLocationDto> listLocations(Long warehouseId, boolean pickableOnly) {
        loadWarehouse(warehouseId);
        List<StorageLocation> rows = pickableOnly
                ? storageLocationRepository.findPickableByWarehouse(warehouseId)
                : storageLocationRepository.findLiveByWarehouse(warehouseId);
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public StorageLocationDto createLocation(Long warehouseId, StorageLocationDto dto) {
        Warehouse w = loadWarehouse(warehouseId);

        if (!w.isBinTracked()) {
            throw new IllegalStateException("Warehouse " + w.getCode()
                    + " is not bin-tracked. Turn on bin tracking before adding locations.");
        }
        String code = requireCode(dto.code());
        storageLocationRepository.findLiveByWarehouseAndCode(warehouseId, code).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Location " + code + " already exists in warehouse " + w.getCode());
        });

        StorageLocation loc = new StorageLocation();
        loc.setWarehouse(w);
        loc.setCode(code);
        apply(loc, dto);
        return toDto(storageLocationRepository.save(loc));
    }

    @Override
    @Transactional
    public StorageLocationDto updateLocation(Long locationId, StorageLocationDto dto) {
        StorageLocation loc = storageLocationRepository.findLiveById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Storage location not found: " + locationId));
        apply(loc, dto);
        loc.setUpdatedDate(new Date());
        return toDto(storageLocationRepository.save(loc));
    }

    @Override
    @Transactional
    public void deleteLocation(Long locationId) {
        StorageLocation loc = storageLocationRepository.findLiveById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Storage location not found: " + locationId));
        loc.setDeletedDate(new Date());
        storageLocationRepository.save(loc);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Warehouse loadWarehouse(Long id) {
        return warehouseRepository.findLiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + id));
    }

    private String requireCode(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Code is required");
        return raw.trim().toUpperCase();
    }

    private void clearOtherDefaults(Long keepId) {
        for (Warehouse other : warehouseRepository.findOtherDefaults(keepId == null ? -1L : keepId)) {
            other.setDefault(false);
            other.setUpdatedDate(new Date());
            warehouseRepository.save(other);
            logger.info("Warehouse {} is no longer the default", other.getCode());
        }
    }

    private void apply(Warehouse w, WarehouseDto dto) {
        if (dto.name() != null)  w.setName(dto.name().trim());
        w.setWarehouseType(dto.warehouseType() != null ? dto.warehouseType() : WarehouseType.GENERAL);
        w.setAddressLine1(dto.addressLine1());
        w.setAddressLine2(dto.addressLine2());
        w.setCity(dto.city());
        w.setState(dto.state());
        w.setPincode(dto.pincode());
        w.setGstin(dto.gstin());
        w.setDefault(dto.isDefault());
        w.setBinTracked(dto.binTracked());
        w.setActive(dto.active());
    }

    private void apply(StorageLocation loc, StorageLocationDto dto) {
        if (dto.code() != null && !dto.code().isBlank()) loc.setCode(dto.code().trim().toUpperCase());
        loc.setAisle(dto.aisle());
        loc.setRack(dto.rack());
        loc.setBin(dto.bin());
        loc.setPickable(dto.pickable());
        loc.setActive(dto.active());
    }

    private WarehouseDto toDto(Warehouse w) {
        return new WarehouseDto(
                w.getId(), w.getCode(), w.getName(), w.getWarehouseType(),
                w.getAddressLine1(), w.getAddressLine2(), w.getCity(), w.getState(),
                w.getPincode(), w.getGstin(), w.isDefault(), w.isBinTracked(), w.isActive(),
                storageLocationRepository.countLiveByWarehouse(w.getId()));
    }

    private StorageLocationDto toDto(StorageLocation loc) {
        return new StorageLocationDto(
                loc.getId(),
                loc.getWarehouse() != null ? loc.getWarehouse().getId() : null,
                loc.getWarehouse() != null ? loc.getWarehouse().getCode() : null,
                loc.getCode(), loc.getAisle(), loc.getRack(), loc.getBin(),
                loc.isPickable(), loc.isActive());
    }
}
