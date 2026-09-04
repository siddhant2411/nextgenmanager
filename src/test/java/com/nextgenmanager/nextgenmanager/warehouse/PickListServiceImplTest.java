package com.nextgenmanager.nextgenmanager.warehouse;

import com.nextgenmanager.nextgenmanager.Inventory.dto.PickConfirmRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.PickListCreateRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.PickListRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.PickListNumberGenerator;
import com.nextgenmanager.nextgenmanager.Inventory.service.PickListServiceImpl;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Picking decides which physical units satisfy an already-reserved order. The rules that matter
 * are the ones stopping a unit being promised twice, taken from the wrong place, or recorded as
 * picked when the numbers do not add up.
 */
@ExtendWith(MockitoExtension.class)
class PickListServiceImplTest {

    @Mock private PickListRepository pickListRepository;
    @Mock private InventoryInstanceRepository inventoryInstanceRepository;
    @Mock private SalesOrderRepository salesOrderRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private PickListNumberGenerator numberGenerator;

    @InjectMocks private PickListServiceImpl service;

    private static final int ITEM_ID = 77;

    private Warehouse main, spare;
    private InventoryItem trackedItem, plainItem;

    private Warehouse warehouse(Long id, String code) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode(code);
        return w;
    }

    private InventoryItem item(int id, String code, boolean tracked) {
        InventoryItem i = new InventoryItem();
        i.setInventoryItemId(id);
        i.setItemCode(code);
        i.setName(code);
        ProductInventorySettings s = new ProductInventorySettings();
        s.setBatchTracked(tracked);
        i.setProductInventorySettings(s);
        return i;
    }

    @BeforeEach
    void setUp() {
        main  = warehouse(1L, "MAIN");
        spare = warehouse(2L, "SPARE");
        trackedItem = item(ITEM_ID, "VALVE-1", true);
        plainItem   = item(ITEM_ID, "BOLT-1", false);

        lenient().when(pickListRepository.save(any(PickList.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(inventoryInstanceRepository.findByPickListLineId(any())).thenReturn(List.of());
    }

    private InventoryInstance instance(long id, InventoryItem item, Warehouse w, String qty) {
        InventoryInstance inst = new InventoryInstance();
        inst.setId(id);
        inst.setInventoryItem(item);
        inst.setWarehouse(w);
        inst.setQuantity(new BigDecimal(qty));
        inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
        return inst;
    }

    private PickList pickOf(InventoryItem item, String toPick, PickListStatus status) {
        SalesOrder so = new SalesOrder();
        so.setId(5L);
        so.setOrderNumber("SO-1");

        PickList p = new PickList();
        p.setId(3L);
        p.setPickNumber("PK/0001");
        p.setSalesOrder(so);
        p.setWarehouse(main);
        p.setStatus(status);

        PickListLine line = new PickListLine();
        line.setId(30L);
        line.setPickList(p);
        line.setInventoryItem(item);
        line.setQuantityToPick(new BigDecimal(toPick));
        line.setQuantityPicked(BigDecimal.ZERO);
        p.getLines().add(line);
        return p;
    }

    // ─── allocation rules ─────────────────────────────────────────────────────

    @Test
    void confirmingATrackedLineAllocatesTheNamedInstances() {
        PickList pick = pickOf(trackedItem, "3", PickListStatus.RELEASED);
        when(pickListRepository.findLiveById(3L)).thenReturn(Optional.of(pick));
        InventoryInstance a = instance(101L, trackedItem, main, "2");
        InventoryInstance b = instance(102L, trackedItem, main, "1");
        when(inventoryInstanceRepository.findById(101L)).thenReturn(Optional.of(a));
        when(inventoryInstanceRepository.findById(102L)).thenReturn(Optional.of(b));

        service.confirm(3L, new PickConfirmRequest("ravi", null,
                List.of(new PickConfirmRequest.Line(30L, new BigDecimal("3"), List.of(101L, 102L)))));

        assertThat(a.getPickListLine()).isSameAs(pick.getLines().get(0));
        assertThat(b.getPickListLine()).isSameAs(pick.getLines().get(0));
        assertThat(pick.getStatus()).isEqualTo(PickListStatus.PICKED);
        assertThat(pick.getPickedBy()).isEqualTo("ravi");
    }

    @Test
    void aTrackedItemCannotBePickedWithoutNamingItsInstances() {
        when(pickListRepository.findLiveById(3L))
                .thenReturn(Optional.of(pickOf(trackedItem, "3", PickListStatus.RELEASED)));

        assertThatThrownBy(() -> service.confirm(3L, new PickConfirmRequest(null, null,
                List.of(new PickConfirmRequest.Line(30L, new BigDecimal("3"), List.of())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name the instances");
    }

    @Test
    void instancesFromAnotherWarehouseAreRefused() {
        when(pickListRepository.findLiveById(3L))
                .thenReturn(Optional.of(pickOf(trackedItem, "1", PickListStatus.RELEASED)));
        when(inventoryInstanceRepository.findById(101L))
                .thenReturn(Optional.of(instance(101L, trackedItem, spare, "1")));

        assertThatThrownBy(() -> service.confirm(3L, new PickConfirmRequest(null, null,
                List.of(new PickConfirmRequest.Line(30L, BigDecimal.ONE, List.of(101L))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in MAIN");
    }

    @Test
    void anInstanceAlreadyOnAnotherPickCannotBeTakenTwice() {
        PickList pick = pickOf(trackedItem, "1", PickListStatus.RELEASED);
        when(pickListRepository.findLiveById(3L)).thenReturn(Optional.of(pick));

        PickList other = pickOf(trackedItem, "1", PickListStatus.PICKED);
        other.setPickNumber("PK/0009");
        other.getLines().get(0).setId(99L);

        InventoryInstance taken = instance(101L, trackedItem, main, "1");
        taken.setPickListLine(other.getLines().get(0));
        when(inventoryInstanceRepository.findById(101L)).thenReturn(Optional.of(taken));

        assertThatThrownBy(() -> service.confirm(3L, new PickConfirmRequest(null, null,
                List.of(new PickConfirmRequest.Line(30L, BigDecimal.ONE, List.of(101L))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already allocated to pick PK/0009");
    }

    @Test
    void instanceQuantitiesMustAddUpToTheQuantityClaimed() {
        when(pickListRepository.findLiveById(3L))
                .thenReturn(Optional.of(pickOf(trackedItem, "5", PickListStatus.RELEASED)));
        when(inventoryInstanceRepository.findById(101L))
                .thenReturn(Optional.of(instance(101L, trackedItem, main, "2")));

        // Claiming 5 while naming 2 units of stock would leave the books saying two things at once.
        assertThatThrownBy(() -> service.confirm(3L, new PickConfirmRequest(null, null,
                List.of(new PickConfirmRequest.Line(30L, new BigDecimal("5"), List.of(101L))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add up to 2");
    }

    @Test
    void pickingMoreThanWasAskedForIsRefused() {
        when(pickListRepository.findLiveById(3L))
                .thenReturn(Optional.of(pickOf(plainItem, "4", PickListStatus.RELEASED)));

        assertThatThrownBy(() -> service.confirm(3L, new PickConfirmRequest(null, null,
                List.of(new PickConfirmRequest.Line(30L, new BigDecimal("6"), List.of())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and the requested 4");
    }

    @Test
    void aShortPickIsRecordedRatherThanRoundedUp() {
        PickList pick = pickOf(plainItem, "4", PickListStatus.RELEASED);
        when(pickListRepository.findLiveById(3L)).thenReturn(Optional.of(pick));

        service.confirm(3L, new PickConfirmRequest(null, null,
                List.of(new PickConfirmRequest.Line(30L, new BigDecimal("3"), List.of()))));

        assertThat(pick.getLines().get(0).getQuantityPicked()).isEqualByComparingTo("3");
        assertThat(pick.getLines().get(0).getQuantityToPick()).isEqualByComparingTo("4");
    }

    @Test
    void anUntrackedLineLeftUnnamedIsPickedInFull() {
        PickList pick = pickOf(plainItem, "4", PickListStatus.RELEASED);
        when(pickListRepository.findLiveById(3L)).thenReturn(Optional.of(pick));

        service.confirm(3L, null);

        assertThat(pick.getLines().get(0).getQuantityPicked()).isEqualByComparingTo("4");
    }

    // ─── lifecycle ────────────────────────────────────────────────────────────

    @Test
    void cancellingReleasesEveryAllocation() {
        PickList pick = pickOf(trackedItem, "1", PickListStatus.PICKED);
        pick.getLines().get(0).setQuantityPicked(BigDecimal.ONE);
        when(pickListRepository.findLiveById(3L)).thenReturn(Optional.of(pick));

        InventoryInstance held = instance(101L, trackedItem, main, "1");
        held.setPickListLine(pick.getLines().get(0));
        when(inventoryInstanceRepository.findByPickListLineId(30L)).thenReturn(List.of(held));

        service.cancel(3L);

        // Otherwise the unit stays claimed by a pick that no longer exists and can never be picked again.
        assertThat(held.getPickListLine()).isNull();
        assertThat(pick.getLines().get(0).getQuantityPicked()).isEqualByComparingTo("0");
        assertThat(pick.getStatus()).isEqualTo(PickListStatus.CANCELLED);
    }

    @Test
    void releaseOnlyWorksFromDraft() {
        when(pickListRepository.findLiveById(3L))
                .thenReturn(Optional.of(pickOf(plainItem, "1", PickListStatus.PICKED)));

        assertThatThrownBy(() -> service.release(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("it is PICKED, not DRAFT");
    }

    @Test
    void anUnapprovedOrderCannotBePicked() {
        SalesOrder draft = new SalesOrder();
        draft.setId(5L);
        draft.setOrderNumber("SO-1");
        draft.setStatus(SalesOrderStatus.DRAFT);
        when(salesOrderRepository.findById(5L)).thenReturn(Optional.of(draft));

        // Found live: picking happily built a list for a DRAFT order, putting stock on a trolley
        // against a document that may never become an order. The delivery note already refuses this.
        assertThatThrownBy(() -> service.createFromSalesOrder(new PickListCreateRequest(5L, "MAIN", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("it is DRAFT");

        verifyNoInteractions(numberGenerator);
    }

    @Test
    void aCancelledOrderCannotBePicked() {
        SalesOrder cancelled = new SalesOrder();
        cancelled.setId(5L);
        cancelled.setOrderNumber("SO-1");
        cancelled.setStatus(SalesOrderStatus.CANCELLED);
        when(salesOrderRepository.findById(5L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.createFromSalesOrder(new PickListCreateRequest(5L, "MAIN", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("it is CANCELLED");
    }

    @Test
    void anOrderWithNothingLeftToPickDoesNotConsumeADocumentNumber() {
        SalesOrder so = new SalesOrder();
        so.setId(5L);
        so.setOrderNumber("SO-1");
        so.setStatus(SalesOrderStatus.APPROVED);
        SalesOrderItem soItem = new SalesOrderItem();
        soItem.setInventoryItem(plainItem);
        soItem.setQty(new BigDecimal("4"));
        so.setItems(new ArrayList<>(List.of(soItem)));

        when(salesOrderRepository.findById(5L)).thenReturn(Optional.of(so));
        when(warehouseService.resolveByCodeOrDefault(any())).thenReturn(main);
        // everything already covered by an existing live pick
        when(pickListRepository.sumAlreadyOnPicks(5L, ITEM_ID)).thenReturn(new BigDecimal("4"));

        assertThatThrownBy(() -> service.createFromSalesOrder(new PickListCreateRequest(5L, "MAIN", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nothing left to pick");

        verifyNoInteractions(numberGenerator);
    }
}
