package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstanceStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickList;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListLine;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.PickListRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteItemDto;
import com.nextgenmanager.nextgenmanager.sales.exception.InvalidSalesOrderStateException;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import com.nextgenmanager.nextgenmanager.sales.repository.DeliveryNoteRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Shipping a confirmed pick.
 *
 * <p>The point of the pick is that the decision about which units leave was made on the floor by
 * someone holding them. These tests are about the delivery note honouring that decision rather
 * than making its own — and about the ways a pick can be spent twice, shipped for the wrong
 * order, or quietly rounded on the way out.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryNoteFromPickTest {

    @Mock private DeliveryNoteRepository deliveryNoteRepository;
    @Mock private SalesOrderRepository salesOrderRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryInstanceService inventoryInstanceService;
    @Mock private InventoryTransactionService inventoryTransactionService;
    @Mock private NumberSequenceRepository numberSequenceRepository;
    @Mock private StoreInventoryRequestService storeInventoryRequestService;
    @Mock private PickListRepository pickListRepository;
    @Mock private InventoryInstanceRepository inventoryInstanceRepository;
    @Mock private com.nextgenmanager.nextgenmanager.packaging.repository.PackingSlipRepository packingSlipRepository;

    @InjectMocks private DeliveryNoteServiceImpl service;

    private static final int ITEM_ID = 77;
    private static final Long SO_ID = 5L;
    private static final Long PICK_ID = 3L;
    private static final Long LINE_ID = 30L;

    private Warehouse main;
    private InventoryItem valve;
    private SalesOrder order;

    @BeforeEach
    void setUp() {
        main = new Warehouse();
        main.setId(1L);
        main.setCode("MAIN");

        valve = new InventoryItem();
        valve.setInventoryItemId(ITEM_ID);
        valve.setItemCode("VALVE-1");
        valve.setName("Flush bottom valve");
        ProductInventorySettings settings = new ProductInventorySettings();
        settings.setBatchTracked(true);
        settings.setAvailableQuantity(10);
        valve.setProductInventorySettings(settings);

        order = new SalesOrder();
        order.setId(SO_ID);
        order.setOrderNumber("SO-1");
        order.setStatus(SalesOrderStatus.APPROVED);
        SalesOrderItem soItem = new SalesOrderItem();
        soItem.setId(50L);
        soItem.setInventoryItem(valve);
        soItem.setQty(new BigDecimal("5"));
        order.setItems(new ArrayList<>(List.of(soItem)));

        lenient().when(salesOrderRepository.findById(SO_ID)).thenReturn(Optional.of(order));
        lenient().when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(valve));
        lenient().when(deliveryNoteRepository.findAll()).thenReturn(List.of());
        // Most picks here were never packed. The packing tests override this.
        lenient().when(packingSlipRepository.findLiveByPickList(any())).thenReturn(Optional.empty());
        lenient().when(packingSlipRepository.findByDeliveryNote(any())).thenReturn(Optional.empty());
        lenient().when(deliveryNoteRepository.save(any(DeliveryNote.class))).thenAnswer(i -> {
            DeliveryNote saved = i.getArgument(0);
            saved.setId(900L);
            return saved;
        });
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────

    private PickList pick(PickListStatus status, String toPick, String picked) {
        PickList p = new PickList();
        p.setId(PICK_ID);
        p.setPickNumber("PK/0001");
        p.setSalesOrder(order);
        p.setWarehouse(main);
        p.setStatus(status);

        PickListLine line = new PickListLine();
        line.setId(LINE_ID);
        line.setPickList(p);
        line.setInventoryItem(valve);
        line.setQuantityToPick(new BigDecimal(toPick));
        line.setQuantityPicked(new BigDecimal(picked));
        p.getLines().add(line);
        return p;
    }

    private InventoryInstance instance(long id, String qty, InventoryInstanceStatus status) {
        InventoryInstance inst = new InventoryInstance();
        inst.setId(id);
        inst.setInventoryItem(valve);
        inst.setWarehouse(main);
        inst.setQuantity(new BigDecimal(qty));
        inst.setCostPerUnit(new BigDecimal("100"));
        inst.setInventoryInstanceStatus(status);
        return inst;
    }

    /** The two units this pick took: reserved for the order at approval, as picking assumes. */
    private List<InventoryInstance> allocateTwoUnits() {
        List<InventoryInstance> instances = List.of(
                instance(101L, "2", InventoryInstanceStatus.REQUESTED),
                instance(102L, "1", InventoryInstanceStatus.REQUESTED));
        when(inventoryInstanceRepository.findByPickListLineId(LINE_ID)).thenReturn(instances);
        lenient().when(inventoryInstanceRepository.findAllById(List.of(101L, 102L)))
                .thenReturn(instances);
        lenient().when(inventoryInstanceService.consumeSpecificInstances(
                        any(), any(), anyDouble(), anyString()))
                .thenReturn(instances);
        return instances;
    }

    private DeliveryNoteCreateDto shipPick() {
        DeliveryNoteCreateDto dto = new DeliveryNoteCreateDto();
        dto.setSalesOrderId(SO_ID);
        dto.setPickListId(PICK_ID);
        dto.setDeliveryNoteNo("DC/2026/0001");
        dto.setDeliveryDate(new Date());
        return dto;
    }

    // ─── packing, once started, has to finish ─────────────────────────────────

    private com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip slip(
            PickList p, com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus status) {
        var s = new com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip();
        s.setId(9L);
        s.setSlipNumber("PS/0001");
        s.setPickList(p);
        s.setSalesOrder(order);
        s.setStatus(status);
        return s;
    }

    @Test
    void aPickNobodyStartedPackingShipsAsBefore() {
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(pick(PickListStatus.PICKED, "5", "3")));
        allocateTwoUnits();

        DeliveryNoteDto note = service.createDeliveryNote(shipPick());

        assertThat(note.getItems()).hasSize(1);
        verify(packingSlipRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void aPickStillBeingBoxedCannotShip() {
        PickList p = pick(PickListStatus.PICKED, "5", "3");
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(p));
        when(packingSlipRepository.findLiveByPickList(PICK_ID)).thenReturn(Optional.of(
                slip(p, com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus.DRAFT)));

        assertThatThrownBy(() -> service.createDeliveryNote(shipPick()))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("PK/0001 is being packed on PS/0001, which is still DRAFT")
                .hasMessageContaining("Finish boxing it");
        verify(deliveryNoteRepository, org.mockito.Mockito.never()).save(any());
    }

    /**
     * PACKED is where a failed or unjudged package inspection holds a slip. Shipping from here is
     * exactly the hole this closes: the goods would leave with the inspection still open.
     */
    @Test
    void aPackedButUnclosedSlipCannotShip() {
        PickList p = pick(PickListStatus.PICKED, "5", "3");
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(p));
        when(packingSlipRepository.findLiveByPickList(PICK_ID)).thenReturn(Optional.of(
                slip(p, com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus.PACKED)));

        assertThatThrownBy(() -> service.createDeliveryNote(shipPick()))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("which is still PACKED")
                .hasMessageContaining("has to pass or be waived first");
        verify(deliveryNoteRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void aClosedSlipShipsAndRemembersTheDeliveryNote() {
        PickList p = pick(PickListStatus.PICKED, "5", "3");
        var closed = slip(p, com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus.CLOSED);
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(p));
        when(packingSlipRepository.findLiveByPickList(PICK_ID)).thenReturn(Optional.of(closed));
        allocateTwoUnits();

        service.createDeliveryNote(shipPick());

        assertThat(closed.getDeliveryNote()).isNotNull();
        assertThat(closed.getDeliveryNote().getDeliveryNoteNo()).isEqualTo("DC/2026/0001");
        verify(packingSlipRepository).save(closed);
    }

    // ─── the pick decides what ships ──────────────────────────────────────────

    @Test
    void shippingAPickConsumesExactlyTheUnitsThePickerTook() {
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(pick(PickListStatus.PICKED, "5", "3")));
        allocateTwoUnits();

        DeliveryNoteDto note = service.createDeliveryNote(shipPick());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(inventoryInstanceService).consumeSpecificInstances(
                eq(valve), ids.capture(), eq(3.0), eq("DC/2026/0001"));
        assertThat(ids.getValue()).containsExactly(101L, 102L);
        assertThat(note.getItems()).hasSize(1);
        assertThat(note.getItems().get(0).getQuantityDelivered()).isEqualByComparingTo("3");
    }

    @Test
    void aShortPickShipsWhatWasFoundNotWhatWasOrdered() {
        // Ordered 5, found 3. The delivery note is for 3; the missing 2 stay on the order.
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(pick(PickListStatus.PICKED, "5", "3")));
        allocateTwoUnits();

        service.createDeliveryNote(shipPick());

        ArgumentCaptor<SalesOrder> so = ArgumentCaptor.forClass(SalesOrder.class);
        verify(salesOrderRepository).save(so.capture());
        assertThat(so.getValue().getStatus()).isEqualTo(SalesOrderStatus.PARTIALLY_DISPATCHED);
    }

    @Test
    void theSpentPickIsMarkedDispatchedAndTiedToTheNote() {
        PickList pick = pick(PickListStatus.PICKED, "5", "3");
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(pick));
        allocateTwoUnits();

        service.createDeliveryNote(shipPick());

        assertThat(pick.getStatus()).isEqualTo(PickListStatus.DISPATCHED);
        assertThat(pick.getDeliveryNote()).isNotNull();
        assertThat(pick.getDeliveryNote().getDeliveryNoteNo()).isEqualTo("DC/2026/0001");
        verify(pickListRepository).save(pick);
    }

    @Test
    void theDispatchIsBookedAgainstTheWarehouseThePickerWalkedTo() {
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(pick(PickListStatus.PICKED, "5", "3")));
        allocateTwoUnits();

        service.createDeliveryNote(shipPick());

        // Left blank the ledger resolves the movement to the default warehouse, which is only
        // ever right by accident. A pick knows where the goods actually were.
        ArgumentCaptor<InventoryTransactionDTO> ledger = ArgumentCaptor.forClass(InventoryTransactionDTO.class);
        verify(inventoryTransactionService).writeDispatchLedger(ledger.capture());
        assertThat(ledger.getValue().getWarehouse()).isEqualTo("MAIN");
        assertThat(ledger.getValue().getTransactionType()).isEqualTo("SALES_DISPATCH");
        assertThat(ledger.getValue().getQuantity()).isEqualTo(3.0);
    }

    @Test
    void theWarehouseCountersAreLeftToWhoeverConsumesTheInstances() {
        // The delivery note used to move the per-warehouse counters itself. Consumption moves
        // them now, one instance at a time, so a second set of arithmetic here would take the
        // same stock off the shelf twice.
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(pick(PickListStatus.PICKED, "5", "3")));
        allocateTwoUnits();

        service.createDeliveryNote(shipPick());

        verify(inventoryTransactionService, only()).writeDispatchLedger(any());
    }

    // ─── what is refused ──────────────────────────────────────────────────────

    @Test
    void aPickThatHasAlreadyShippedCannotShipAgain() {
        PickList spent = pick(PickListStatus.DISPATCHED, "5", "3");
        DeliveryNote earlier = new DeliveryNote();
        earlier.setDeliveryNoteNo("DC/2026/0007");
        spent.setDeliveryNote(earlier);
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(spent));

        assertThatThrownBy(() -> service.createDeliveryNote(shipPick()))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("already been shipped on DC/2026/0007");
    }

    @Test
    void aPickNobodyHasConfirmedCannotShip() {
        when(pickListRepository.findLiveById(PICK_ID))
                .thenReturn(Optional.of(pick(PickListStatus.RELEASED, "5", "0")));

        assertThatThrownBy(() -> service.createDeliveryNote(shipPick()))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("Confirm the pick");
    }

    @Test
    void aPickBelongingToAnotherOrderIsRefused() {
        SalesOrder other = new SalesOrder();
        other.setId(6L);
        other.setOrderNumber("SO-2");
        PickList pick = pick(PickListStatus.PICKED, "5", "3");
        pick.setSalesOrder(other);
        when(pickListRepository.findLiveById(PICK_ID)).thenReturn(Optional.of(pick));

        assertThatThrownBy(() -> service.createDeliveryNote(shipPick()))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("picked for SO-2, not SO-1");
    }

    @Test
    void aPickWhereNothingWasFoundHasNothingToShip() {
        when(pickListRepository.findLiveById(PICK_ID))
                .thenReturn(Optional.of(pick(PickListStatus.PICKED, "5", "0")));

        assertThatThrownBy(() -> service.createDeliveryNote(shipPick()))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("Nothing was picked on PK/0001");
    }

    /**
     * Goods sold by weight are picked in fractions. The delivery note used to hold whole numbers,
     * so the only honest thing it could do with 2.5 was refuse; it now ships exactly what was
     * picked, and consumes exactly that much stock.
     */
    @Test
    void aFractionalPickShipsExactlyWhatWasPicked() {
        when(pickListRepository.findLiveById(PICK_ID))
                .thenReturn(Optional.of(pick(PickListStatus.PICKED, "5", "2.5")));
        allocateTwoUnits();

        DeliveryNoteDto note = service.createDeliveryNote(shipPick());

        assertThat(note.getItems().get(0).getQuantityDelivered()).isEqualByComparingTo("2.5");
        verify(inventoryInstanceService).consumeSpecificInstances(
                eq(valve), any(), eq(2.5), eq("DC/2026/0001"));
    }

    @Test
    void aFractionalPickCannotExceedWhatIsLeftOnTheOrder() {
        // Ordered 5. A pick claiming 5.5 must be refused, and the message must show the decimals.
        when(pickListRepository.findLiveById(PICK_ID))
                .thenReturn(Optional.of(pick(PickListStatus.PICKED, "6", "5.5")));
        allocateTwoUnits();

        assertThatThrownBy(() -> service.createDeliveryNote(shipPick()))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("dispatch qty 5.5 exceeds remaining 5");
    }

    // ─── the direct-dispatch bypass ───────────────────────────────────────────

    @Test
    void aWaitingPickBlocksADeliveryNoteRaisedByHand() {
        // Allocating a second set of units for stock already on a trolley is the exact drift the
        // pick exists to prevent, so the bypass has to be asked for rather than fallen into.
        when(pickListRepository.findPickedAwaitingDispatch(SO_ID))
                .thenReturn(List.of(pick(PickListStatus.PICKED, "5", "3")));

        DeliveryNoteCreateDto byHand = new DeliveryNoteCreateDto();
        byHand.setSalesOrderId(SO_ID);
        byHand.setDeliveryNoteNo("DC/2026/0002");
        byHand.setItems(List.of(new DeliveryNoteItemDto(ITEM_ID, new BigDecimal("3"), List.of(101L))));

        assertThatThrownBy(() -> service.createDeliveryNote(byHand))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("confirmed pick waiting to ship (PK/0001)");
    }

    @Test
    void directDispatchStillShipsWithoutAPick() {
        // Counter sales and samples never touch a pick list, and must not be forced through one.
        List<InventoryInstance> instances = List.of(instance(101L, "3", InventoryInstanceStatus.AVAILABLE));
        when(inventoryInstanceService.consumeSpecificInstances(any(), any(), anyDouble(), anyString()))
                .thenReturn(instances);

        DeliveryNoteCreateDto counter = new DeliveryNoteCreateDto();
        counter.setSalesOrderId(SO_ID);
        counter.setDeliveryNoteNo("DC/2026/0003");
        counter.setDirectDispatch(true);
        counter.setItems(List.of(new DeliveryNoteItemDto(ITEM_ID, new BigDecimal("3"), List.of(101L))));

        DeliveryNoteDto note = service.createDeliveryNote(counter);

        assertThat(note.getDeliveryNoteNo()).isEqualTo("DC/2026/0003");
        assertThat(note.getPickNumber()).isNull();
        // No pick, so nothing to mark spent.
        verify(pickListRepository, never()).save(any(PickList.class));
    }
}
