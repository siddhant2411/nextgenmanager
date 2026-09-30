package com.nextgenmanager.nextgenmanager.warehouse;

import com.nextgenmanager.nextgenmanager.Inventory.dto.StorageLocationDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.WarehouseDto;
import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.model.WarehouseType;
import com.nextgenmanager.nextgenmanager.Inventory.repository.StorageLocationRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.WarehouseRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exactly one warehouse may be the default, and a partial unique index in the database
 * enforces it. That makes ordering load-bearing: the incumbent must be cleared <em>and
 * flushed</em> before another row is marked default, or two rows carry the flag at flush
 * time and Postgres rejects a change the caller legitimately asked for.
 *
 * <p>The first cut of this service mutated the target first and returned 409 on every
 * attempt to switch the default. Nothing caught it until the live API was exercised, so the
 * ordering is pinned here.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseServiceImplTest {

    @Mock private WarehouseRepository warehouseRepository;
    @Mock private StorageLocationRepository storageLocationRepository;

    @InjectMocks private WarehouseServiceImpl service;

    private Warehouse warehouse(Long id, String code, boolean isDefault, boolean binTracked) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode(code);
        w.setName(code + " store");
        w.setWarehouseType(WarehouseType.GENERAL);
        w.setDefault(isDefault);
        w.setBinTracked(binTracked);
        w.setActive(true);
        return w;
    }

    private WarehouseDto dto(String code, boolean isDefault, boolean binTracked) {
        return new WarehouseDto(null, code, code + " store", WarehouseType.GENERAL,
                null, null, null, null, null, null, isDefault, binTracked, true, 0);
    }

    private void echoSave() {
        when(warehouseRepository.save(any(Warehouse.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── the ordering that broke ──────────────────────────────────────────────

    @Test
    void switchingDefaultClearsTheIncumbentAndFlushesBeforeMarkingTheNewOne() {
        Warehouse target   = warehouse(2L, "TSTW", false, false);
        Warehouse incumbent = warehouse(1L, "MAIN", true, false);

        when(warehouseRepository.findLiveById(2L)).thenReturn(Optional.of(target));
        when(warehouseRepository.findOtherDefaults(2L)).thenReturn(List.of(incumbent));
        echoSave();

        service.updateWarehouse(2L, dto("TSTW", true, false));

        InOrder order = inOrder(warehouseRepository);
        order.verify(warehouseRepository).save(incumbent);   // incumbent cleared first
        order.verify(warehouseRepository).flush();           // and pushed to the database
        order.verify(warehouseRepository).save(target);      // only then is the target marked

        assertThat(incumbent.isDefault()).isFalse();
        assertThat(target.isDefault()).isTrue();
    }

    @Test
    void creatingADefaultWarehouseAlsoClearsTheIncumbentBeforeInserting() {
        Warehouse incumbent = warehouse(1L, "MAIN", true, false);

        when(warehouseRepository.findLiveByCode("NEWW")).thenReturn(Optional.empty());
        when(warehouseRepository.findOtherDefaults(-1L)).thenReturn(List.of(incumbent));
        echoSave();

        service.createWarehouse(dto("NEWW", true, false));

        InOrder order = inOrder(warehouseRepository);
        order.verify(warehouseRepository).save(incumbent);
        order.verify(warehouseRepository).flush();
        order.verify(warehouseRepository).save(any(Warehouse.class));

        assertThat(incumbent.isDefault()).isFalse();
    }

    @Test
    void anUnrelatedUpdateNeverTouchesTheDefaultFlag() {
        Warehouse w = warehouse(3L, "SPARE", false, false);
        when(warehouseRepository.findLiveById(3L)).thenReturn(Optional.of(w));
        echoSave();

        service.updateWarehouse(3L, dto("SPARE", false, true));

        verify(warehouseRepository, never()).flush();
        verify(warehouseRepository, never()).findOtherDefaults(anyLong());
        assertThat(w.isBinTracked()).isTrue();
    }

    // ─── refusals that keep a fallback warehouse alive ────────────────────────

    @Test
    void theDefaultCannotBeUnsetDirectly() {
        Warehouse w = warehouse(1L, "MAIN", true, false);
        when(warehouseRepository.findLiveById(1L)).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> service.updateWarehouse(1L, dto("MAIN", false, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Make another warehouse the default");

        // refused before anything was mutated
        assertThat(w.isDefault()).isTrue();
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void theDefaultCannotBeDeleted() {
        when(warehouseRepository.findLiveById(1L)).thenReturn(Optional.of(warehouse(1L, "MAIN", true, false)));

        assertThatThrownBy(() -> service.deleteWarehouse(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default warehouse");
    }

    @Test
    void aWarehouseHoldingLocationsCannotBeDeleted() {
        when(warehouseRepository.findLiveById(2L)).thenReturn(Optional.of(warehouse(2L, "TSTW", false, true)));
        when(storageLocationRepository.countLiveByWarehouse(2L)).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteWarehouse(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 location");
    }

    @Test
    void resolveDefaultFailsLoudlyWhenNoneIsConfigured() {
        when(warehouseRepository.findDefault()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveDefaultWarehouse())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No default warehouse");
    }

    // ─── bins ─────────────────────────────────────────────────────────────────

    @Test
    void locationsAreRefusedOnAWarehouseThatIsNotBinTracked() {
        when(warehouseRepository.findLiveById(1L)).thenReturn(Optional.of(warehouse(1L, "MAIN", true, false)));

        StorageLocationDto loc = new StorageLocationDto(null, 1L, "MAIN", "A-01-1",
                "A", "01", "1", true, true);

        assertThatThrownBy(() -> service.createLocation(1L, loc))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not bin-tracked");
    }

    @Test
    void duplicateCodesAreRefused() {
        when(warehouseRepository.findLiveByCode("MAIN"))
                .thenReturn(Optional.of(warehouse(1L, "MAIN", true, false)));

        assertThatThrownBy(() -> service.createWarehouse(dto("MAIN", false, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void codesAreNormalisedToUpperCase() {
        when(warehouseRepository.findLiveByCode("SPARE")).thenReturn(Optional.empty());
        echoSave();

        WarehouseDto result = service.createWarehouse(dto("  spare  ", false, false));

        assertThat(result.code()).isEqualTo("SPARE");
    }
}
