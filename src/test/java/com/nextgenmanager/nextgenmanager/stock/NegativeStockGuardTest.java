package com.nextgenmanager.nextgenmanager.stock;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryLedger;
import com.nextgenmanager.nextgenmanager.Inventory.model.ItemWarehouseStock;
import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.Inventory.service.BatchSerialService;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionServiceImpl;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.ItemWarehouseStockRepository;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code allowNegativeStock} was declared on ProductInventorySettings and read nowhere.
 * The only quantity checks lived inline in reserveStock and consumeStock and were gated
 * on {@code isTracked}, so untracked items bypassed them completely and adjustStock had
 * no check at all — which is how items with the flag off reached negative balances.
 *
 * <p>These tests pin the guard down at all three sites, for untracked items specifically,
 * because that is the path that had no protection.
 */
@ExtendWith(MockitoExtension.class)
class NegativeStockGuardTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryLedgerRepository inventoryLedgerRepository;
    @Mock private InventoryInstanceRepository inventoryInstanceRepository;
    @Mock private BatchSerialService batchSerialService;
    @Mock private DomainEventPublisher eventPublisher;
    @Mock private WarehouseService warehouseService;
    @Mock private ItemWarehouseStockRepository itemWarehouseStockRepository;

    @InjectMocks private InventoryTransactionServiceImpl service;

    private static final int ITEM_ID = 42;

    /** An untracked item — the path that previously had no guard at all. */
    private InventoryItem itemWith(double available, double reserved, boolean allowNegative) {
        ProductInventorySettings settings = new ProductInventorySettings();
        settings.setAvailableQuantity(available);
        settings.setReservedQuantity(reserved);
        settings.setBatchTracked(false);
        settings.setSerialTracked(false);
        settings.setAllowNegativeStock(allowNegative);

        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(ITEM_ID);
        item.setItemCode("STRBDY30001");
        item.setProductInventorySettings(settings);
        return item;
    }

    private InventoryTransactionDTO req(double qty) {
        InventoryTransactionDTO dto = new InventoryTransactionDTO();
        dto.setInventoryItemId(ITEM_ID);
        dto.setQuantity(qty);
        dto.setTransactionType("TEST");
        return dto;
    }

    private void stubItem(InventoryItem item) {
        when(inventoryItemRepository.findByActiveId(ITEM_ID)).thenReturn(item);
    }

    /**
     * writeLedger dereferences the saved row and, since V164, resolves a warehouse for the
     * NOT NULL foreign key. Both are needed only on the paths that actually reach it.
     */
    private void stubLedgerSave() {
        InventoryLedger saved = mock(InventoryLedger.class);
        when(saved.getId()).thenReturn(1L);
        when(inventoryLedgerRepository.save(any(InventoryLedger.class))).thenReturn(saved);
        when(warehouseService.resolveByCodeOrDefault(any())).thenReturn(new Warehouse());
    }

    @Test
    void reserveIsRefusedWhenItWouldGoNegativeOnAnUntrackedItem() {
        stubItem(itemWith(3, 0, false));

        assertThatThrownBy(() -> service.reserveStock(req(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRBDY30001")
                .hasMessageContaining("not configured to allow negative stock");
    }

    @Test
    void consumeIsRefusedWhenItWouldGoNegativeOnAnUntrackedItem() {
        stubItem(itemWith(1, 1, false));

        // reserved + available = 2, so consuming 5 must be refused
        assertThatThrownBy(() -> service.consumeStock(req(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consume");
    }

    @Test
    void adjustDownIsRefusedWhenItWouldGoNegative() {
        stubItem(itemWith(2, 0, false));

        // adjustStock previously had no quantity check whatsoever
        assertThatThrownBy(() -> service.adjustStock(req(-5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adjust down");
    }

    @Test
    void allowNegativeStockStillPermitsGoingBelowZero() {
        InventoryItem item = itemWith(2, 0, true);
        stubItem(item);
        stubLedgerSave();

        assertThatCode(() -> service.adjustStock(req(-5))).doesNotThrowAnyException();
        assertThat(item.getProductInventorySettings().getAvailableQuantity()).isEqualTo(-3);
    }

    @Test
    void anAdjustmentIsMirroredIntoThePerWarehouseCounters() {
        InventoryItem item = itemWith(10, 0, false);
        stubItem(item);
        stubLedgerSave();

        service.adjustStock(req(-4));

        // The company-wide counter and the warehouse row must move by the same amount, or the
        // invariant the reconciliation report checks stops holding.
        ArgumentCaptor<ItemWarehouseStock> saved = ArgumentCaptor.forClass(ItemWarehouseStock.class);
        verify(itemWarehouseStockRepository).save(saved.capture());

        assertThat(saved.getValue().getOnHand()).isEqualByComparingTo("-4");
        assertThat(item.getProductInventorySettings().getAvailableQuantity()).isEqualTo(6);
    }

    @Test
    void anAdjustmentAccumulatesOntoAnExistingWarehouseRow() {
        InventoryItem item = itemWith(10, 0, false);
        stubItem(item);
        stubLedgerSave();

        // The upsert's other branch: a row already exists, so the delta must accumulate onto it
        // rather than replace it. Getting this wrong would silently reset a warehouse to the
        // size of the last movement.
        ItemWarehouseStock existing = new ItemWarehouseStock();
        existing.setOnHand(new java.math.BigDecimal("7"));
        existing.setReserved(new java.math.BigDecimal("2"));
        when(itemWarehouseStockRepository.find(anyInt(), any())).thenReturn(Optional.of(existing));

        service.adjustStock(req(-4));

        assertThat(existing.getOnHand()).isEqualByComparingTo("3");    // 7 - 4
        assertThat(existing.getReserved()).isEqualByComparingTo("2");  // untouched
        verify(itemWarehouseStockRepository).save(existing);
    }

    @Test
    void movementWithinAvailableStockIsUnaffected() {
        InventoryItem item = itemWith(10, 0, false);
        stubItem(item);
        stubLedgerSave();

        assertThatCode(() -> service.adjustStock(req(-4))).doesNotThrowAnyException();
        assertThat(item.getProductInventorySettings().getAvailableQuantity()).isEqualTo(6);
    }
}
