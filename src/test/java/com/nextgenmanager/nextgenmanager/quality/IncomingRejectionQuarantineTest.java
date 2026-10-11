package com.nextgenmanager.nextgenmanager.quality;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.ItemWarehouseStockRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionServiceImpl;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Setting aside stock an incoming inspection rejected.
 *
 * <p>A goods receipt puts the accepted quantity on the shelf. An inspection raised afterwards
 * could reject part of it and nothing happened: the judgement was a record, and the goods stayed
 * pickable. These tests are about the movement that now follows — out of the store, into
 * quarantine, marked failed — and about the company-wide quantity not changing, because the goods
 * have not left the building.
 */
@ExtendWith(MockitoExtension.class)
class IncomingRejectionQuarantineTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryInstanceRepository inventoryInstanceRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private ItemWarehouseStockRepository itemWarehouseStockRepository;
    @Mock private InventoryLedgerRepository inventoryLedgerRepository;
    @Mock private DomainEventPublisher eventPublisher;

    @InjectMocks private InventoryTransactionServiceImpl service;

    private static final int ITEM_ID = 1752;

    private InventoryItem casting;
    private Warehouse main, quarantine;
    private Map<Long, ItemWarehouseStock> stock;

    private Warehouse warehouse(long id, String code) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode(code);
        return w;
    }

    private void seed(Warehouse w, String onHand) {
        ItemWarehouseStock row = new ItemWarehouseStock();
        row.setInventoryItem(casting);
        row.setWarehouse(w);
        row.setOnHand(new BigDecimal(onHand));
        stock.put(w.getId(), row);
    }

    private BigDecimal onHand(Warehouse w) {
        ItemWarehouseStock row = stock.get(w.getId());
        return row == null ? BigDecimal.ZERO : row.getOnHand();
    }

    private InventoryTransactionDTO rejection(double qty) {
        InventoryTransactionDTO dto = new InventoryTransactionDTO();
        dto.setInventoryItemId(ITEM_ID);
        dto.setQuantity(qty);
        dto.setWarehouse("MAIN");
        dto.setReferenceType("INSPECTION");
        dto.setReferenceDocNo("QC/0007");
        return dto;
    }

    @BeforeEach
    void setUp() {
        main = warehouse(1L, "MAIN");
        quarantine = warehouse(2L, "QUAR");
        stock = new HashMap<>();

        casting = new InventoryItem();
        casting.setInventoryItemId(ITEM_ID);
        casting.setItemCode("RMCF80001");
        ProductInventorySettings settings = new ProductInventorySettings();
        settings.setAvailableQuantity(100);
        casting.setProductInventorySettings(settings);

        lenient().when(inventoryItemRepository.findByActiveId(ITEM_ID)).thenReturn(casting);
        lenient().when(warehouseService.resolveByCodeOrDefault("MAIN")).thenReturn(main);
        lenient().when(warehouseService.resolveByCodeOrDefault("QUAR")).thenReturn(quarantine);
        lenient().when(warehouseService.resolveQuarantineWarehouse()).thenReturn(quarantine);
        lenient().when(itemWarehouseStockRepository.find(anyInt(), any()))
                .thenAnswer(i -> Optional.ofNullable(stock.get(i.<Long>getArgument(1))));
        lenient().when(itemWarehouseStockRepository.save(any(ItemWarehouseStock.class)))
                .thenAnswer(i -> {
                    ItemWarehouseStock row = i.getArgument(0);
                    stock.put(row.getWarehouse().getId(), row);
                    return row;
                });
        lenient().when(inventoryLedgerRepository.save(any(InventoryLedger.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void rejectedStockLeavesTheStoreForQuarantineAsFailed() {
        seed(main, "100");

        double setAside = service.quarantineStock(rejection(10), "GRN-202610-0003");

        assertThat(setAside).isEqualTo(10.0);
        assertThat(onHand(main)).isEqualByComparingTo("90");
        assertThat(onHand(quarantine)).isEqualByComparingTo("10");

        ArgumentCaptor<InventoryInstance> saved = ArgumentCaptor.forClass(InventoryInstance.class);
        verify(inventoryInstanceRepository).save(saved.capture());
        assertThat(saved.getValue().getQualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(saved.getValue().getWarehouse()).isSameAs(quarantine);
        assertThat(saved.getValue().getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void theCompanyStillOwnsTheGoods() {
        // Nothing was bought, sold or scrapped. The quantity the books value must not move, and
        // the ledger row that records the event carries no quantity.
        seed(main, "100");

        service.quarantineStock(rejection(10), "GRN-202610-0003");

        assertThat(casting.getProductInventorySettings().getAvailableQuantity()).isEqualTo(100.0);
        ArgumentCaptor<InventoryLedger> row = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(inventoryLedgerRepository).save(row.capture());
        assertThat(row.getValue().getTransactionType()).isEqualTo("QUARANTINE");
        assertThat(row.getValue().getQuantity()).isEqualTo(0.0);
        assertThat(row.getValue().getReferenceDocNo()).isEqualTo("QC/0007");
    }

    @Test
    void onlyWhatIsStillFreeCanBeSetAside() {
        // The rest was reserved or used before the inspection was recorded. The result still has
        // to be recordable, so the caller is told how much actually moved.
        seed(main, "4");

        double setAside = service.quarantineStock(rejection(10), "GRN-202610-0003");

        assertThat(setAside).isEqualTo(4.0);
        assertThat(onHand(main)).isEqualByComparingTo("0");
        assertThat(onHand(quarantine)).isEqualByComparingTo("4");
    }

    @Test
    void nothingFreeMeansNothingMoves() {
        seed(main, "0");

        assertThat(service.quarantineStock(rejection(10), "GRN-202610-0003")).isEqualTo(0.0);

        verify(inventoryInstanceRepository, never()).save(any(InventoryInstance.class));
        verify(inventoryLedgerRepository, never()).save(any(InventoryLedger.class));
    }

    @Test
    void theReceiptsOwnBatchIsSetAsideBeforeOlderStock() {
        casting.getProductInventorySettings().setBatchTracked(true);
        seed(main, "100");

        BatchNumber lastMonth = new BatchNumber();
        lastMonth.setSourceDocNo("GRN-202609-0001");
        BatchNumber thisReceipt = new BatchNumber();
        thisReceipt.setSourceDocNo("GRN-202610-0003");

        InventoryInstance older = new InventoryInstance();
        older.setInventoryItem(casting);
        older.setWarehouse(main);
        older.setQuantity(new BigDecimal("60"));
        older.setBatchNumber(lastMonth);

        InventoryInstance received = new InventoryInstance();
        received.setInventoryItem(casting);
        received.setWarehouse(main);
        received.setQuantity(new BigDecimal("40"));
        received.setBatchNumber(thisReceipt);

        when(inventoryInstanceRepository.findTransferableUnits(ITEM_ID, 1L))
                .thenReturn(List.of(older, received)); // oldest first, as the query returns them

        service.quarantineStock(rejection(10), "GRN-202610-0003");

        // Last month's accepted stock is untouched; ten come out of the batch just received.
        assertThat(older.getQuantity()).isEqualByComparingTo("60");
        assertThat(older.getQualityStatus()).isEqualTo(QualityStatus.PASSED);
        assertThat(received.getQuantity()).isEqualByComparingTo("30");

        ArgumentCaptor<InventoryInstance> saved = ArgumentCaptor.forClass(InventoryInstance.class);
        verify(inventoryInstanceRepository, times(2)).save(saved.capture());
        InventoryInstance rejected = saved.getAllValues().get(1);
        assertThat(rejected.getQuantity()).isEqualByComparingTo("10");
        assertThat(rejected.getQualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(rejected.getWarehouse()).isSameAs(quarantine);
        assertThat(rejected.getBatchNumber()).isSameAs(thisReceipt);
    }

    @Test
    void withNoQuarantineWarehouseTheGoodsAreStillMarkedFailed() {
        when(warehouseService.resolveQuarantineWarehouse()).thenReturn(null);
        seed(main, "100");

        service.quarantineStock(rejection(10), "GRN-202610-0003");

        // Nowhere to move them to, so the counters stay put — but failed is what keeps them
        // off a pick, and that still happens.
        assertThat(onHand(main)).isEqualByComparingTo("100");
        ArgumentCaptor<InventoryInstance> saved = ArgumentCaptor.forClass(InventoryInstance.class);
        verify(inventoryInstanceRepository).save(saved.capture());
        assertThat(saved.getValue().getQualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(saved.getValue().getWarehouse()).isSameAs(main);
    }
}
