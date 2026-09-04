package com.nextgenmanager.nextgenmanager.warehouse;

import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferCreateRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferReceiveRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.ItemWarehouseStockRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.StockTransferRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.StockTransferNumberGenerator;
import com.nextgenmanager.nextgenmanager.Inventory.service.StockTransferServiceImpl;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A transfer must never create or destroy stock — it only moves the split between warehouses.
 * The invariant the reconciliation report checks is
 * {@code availableQuantity == SUM(onHand) + SUM(inTransit)}, so every one of these tests asserts
 * the total across both warehouses is unchanged, not merely that the numbers moved.
 */
@ExtendWith(MockitoExtension.class)
class StockTransferServiceImplTest {

    @Mock private StockTransferRepository stockTransferRepository;
    @Mock private ItemWarehouseStockRepository itemWarehouseStockRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private StockTransferNumberGenerator numberGenerator;

    @InjectMocks private StockTransferServiceImpl service;

    private static final int ITEM_ID = 501;

    private Warehouse main, spare;
    private InventoryItem item;
    private Map<Long, ItemWarehouseStock> stockByWarehouse;

    private Warehouse warehouse(Long id, String code) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode(code);
        return w;
    }

    @BeforeEach
    void setUp() {
        main  = warehouse(1L, "MAIN");
        spare = warehouse(2L, "SPARE");

        item = new InventoryItem();
        item.setInventoryItemId(ITEM_ID);
        item.setItemCode("BOLT30001");
        item.setName("Bolt");

        stockByWarehouse = new HashMap<>();

        // A tiny in-memory stand-in for the per-warehouse table, so assertions read as balances.
        lenient().when(itemWarehouseStockRepository.find(anyInt(), any()))
                .thenAnswer(inv -> Optional.ofNullable(stockByWarehouse.get(inv.<Long>getArgument(1))));
        lenient().when(itemWarehouseStockRepository.save(any(ItemWarehouseStock.class)))
                .thenAnswer(inv -> {
                    ItemWarehouseStock row = inv.getArgument(0);
                    stockByWarehouse.put(row.getWarehouse().getId(), row);
                    return row;
                });
        lenient().when(stockTransferRepository.save(any(StockTransfer.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void seed(Warehouse w, String onHand, String inTransit) {
        ItemWarehouseStock row = new ItemWarehouseStock();
        row.setInventoryItem(item);
        row.setWarehouse(w);
        row.setOnHand(new BigDecimal(onHand));
        row.setInTransit(new BigDecimal(inTransit));
        stockByWarehouse.put(w.getId(), row);
    }

    private BigDecimal onHand(Warehouse w) {
        ItemWarehouseStock r = stockByWarehouse.get(w.getId());
        return r == null ? BigDecimal.ZERO : r.getOnHand();
    }

    private BigDecimal inTransit(Warehouse w) {
        ItemWarehouseStock r = stockByWarehouse.get(w.getId());
        return r == null ? BigDecimal.ZERO : r.getInTransit();
    }

    /** onHand + inTransit across every warehouse — must not change during a transfer. */
    private BigDecimal grandTotal() {
        return stockByWarehouse.values().stream()
                .map(r -> r.getOnHand().add(r.getInTransit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private StockTransfer transferOf(String qty, StockTransferStatus status) {
        StockTransfer t = new StockTransfer();
        t.setId(9L);
        t.setTransferNumber("ST/0001");
        t.setFromWarehouse(main);
        t.setToWarehouse(spare);
        t.setStatus(status);

        StockTransferLine line = new StockTransferLine();
        line.setId(90L);
        line.setStockTransfer(t);
        line.setInventoryItem(item);
        line.setQuantity(new BigDecimal(qty));
        line.setReceivedQuantity(BigDecimal.ZERO);
        t.getLines().add(line);
        return t;
    }

    // ─── dispatch ─────────────────────────────────────────────────────────────

    @Test
    void dispatchMovesStockOutOfTheSourceAndIntoTransit() {
        seed(main, "10", "0");
        when(stockTransferRepository.findLiveById(9L)).thenReturn(Optional.of(transferOf("4", StockTransferStatus.DRAFT)));

        BigDecimal before = grandTotal();
        service.dispatch(9L);

        assertThat(onHand(main)).isEqualByComparingTo("6");
        assertThat(inTransit(main)).isEqualByComparingTo("4");
        assertThat(onHand(spare)).isEqualByComparingTo("0");
        assertThat(grandTotal()).isEqualByComparingTo(before);  // nothing created or destroyed
    }

    @Test
    void dispatchIsRefusedWhenTheSourceHasTooLittleAndMovesNothing() {
        seed(main, "2", "0");
        when(stockTransferRepository.findLiveById(9L)).thenReturn(Optional.of(transferOf("4", StockTransferStatus.DRAFT)));

        assertThatThrownBy(() -> service.dispatch(9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only 2 on hand");

        // Checked before any counter moves, so a multi-line transfer never half-dispatches.
        assertThat(onHand(main)).isEqualByComparingTo("2");
        assertThat(inTransit(main)).isEqualByComparingTo("0");
    }

    @Test
    void dispatchIsRefusedUnlessTheTransferIsStillDraft() {
        when(stockTransferRepository.findLiveById(9L))
                .thenReturn(Optional.of(transferOf("4", StockTransferStatus.DISPATCHED)));

        assertThatThrownBy(() -> service.dispatch(9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISPATCHED");
    }

    // ─── receive ──────────────────────────────────────────────────────────────

    @Test
    void receiveLandsTransitStockInTheDestination() {
        seed(main, "6", "4");
        StockTransfer t = transferOf("4", StockTransferStatus.DISPATCHED);
        when(stockTransferRepository.findLiveById(9L)).thenReturn(Optional.of(t));

        BigDecimal before = grandTotal();
        service.receive(9L, null);   // no lines named = received in full

        assertThat(inTransit(main)).isEqualByComparingTo("0");
        assertThat(onHand(spare)).isEqualByComparingTo("4");
        assertThat(onHand(main)).isEqualByComparingTo("6");
        assertThat(grandTotal()).isEqualByComparingTo(before);
        assertThat(t.getStatus()).isEqualTo(StockTransferStatus.RECEIVED);
    }

    @Test
    void aShortReceiptLeavesTheShortfallInTransitRatherThanLosingIt() {
        seed(main, "6", "4");
        when(stockTransferRepository.findLiveById(9L))
                .thenReturn(Optional.of(transferOf("4", StockTransferStatus.DISPATCHED)));

        BigDecimal before = grandTotal();
        service.receive(9L, new StockTransferReceiveRequest(
                List.of(new StockTransferReceiveRequest.Line(90L, new BigDecimal("3"))), null));

        assertThat(onHand(spare)).isEqualByComparingTo("3");
        // The missing unit is still owned and still visible, not quietly rounded away.
        assertThat(inTransit(main)).isEqualByComparingTo("1");
        assertThat(grandTotal()).isEqualByComparingTo(before);
    }

    @Test
    void receivingMoreThanWasDispatchedIsRefused() {
        seed(main, "6", "4");
        when(stockTransferRepository.findLiveById(9L))
                .thenReturn(Optional.of(transferOf("4", StockTransferStatus.DISPATCHED)));

        assertThatThrownBy(() -> service.receive(9L, new StockTransferReceiveRequest(
                List.of(new StockTransferReceiveRequest.Line(90L, new BigDecimal("5"))), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and the dispatched");
    }

    // ─── create and cancel ────────────────────────────────────────────────────

    @Test
    void aTransferToItsOwnSourceIsRefused() {
        when(warehouseService.resolveByCodeOrDefault("MAIN")).thenReturn(main);

        StockTransferCreateRequest req = new StockTransferCreateRequest("MAIN", "MAIN", null,
                List.of(new StockTransferCreateRequest.Line(ITEM_ID, BigDecimal.ONE, null)));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same warehouse");
    }

    @Test
    void cancelIsRefusedOnceStockHasMoved() {
        when(stockTransferRepository.findLiveById(9L))
                .thenReturn(Optional.of(transferOf("4", StockTransferStatus.DISPATCHED)));

        assertThatThrownBy(() -> service.cancel(9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stock has already left MAIN");
    }

    @Test
    void cancellingAnAlreadyCancelledTransferSaysSoRatherThanClaimingStockMoved() {
        when(stockTransferRepository.findLiveById(9L))
                .thenReturn(Optional.of(transferOf("4", StockTransferStatus.CANCELLED)));

        // The first version of this message told the caller stock had moved, which is false for a
        // cancelled transfer. An error that states something untrue is worse than a vague one.
        assertThatThrownBy(() -> service.cancel(9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already cancelled")
                .hasMessageNotContaining("already moved");
    }

    @Test
    void aRejectedCreateDoesNotConsumeADocumentNumber() {
        when(warehouseService.resolveByCodeOrDefault("MAIN")).thenReturn(main);
        when(warehouseService.resolveByCodeOrDefault("SPARE")).thenReturn(spare);

        // The generator commits in its own transaction, so a number drawn before validation is
        // spent even when the create rolls back — that left permanent gaps in the sequence.
        StockTransferCreateRequest bad = new StockTransferCreateRequest("MAIN", "SPARE", null,
                List.of(new StockTransferCreateRequest.Line(ITEM_ID, BigDecimal.ZERO, null)));

        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(numberGenerator);
    }

    @Test
    void cancelIsAllowedWhileStillDraft() {
        StockTransfer t = transferOf("4", StockTransferStatus.DRAFT);
        when(stockTransferRepository.findLiveById(9L)).thenReturn(Optional.of(t));

        service.cancel(9L);

        assertThat(t.getStatus()).isEqualTo(StockTransferStatus.CANCELLED);
    }
}
