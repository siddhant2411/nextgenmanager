package com.nextgenmanager.nextgenmanager.packaging;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickList;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListLine;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.PickListRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.packaging.dto.PackageBoxCreateRequest;
import com.nextgenmanager.nextgenmanager.packaging.dto.PackageLineRequest;
import com.nextgenmanager.nextgenmanager.packaging.model.PackageBox;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus;
import com.nextgenmanager.nextgenmanager.packaging.repository.PackageBoxRepository;
import com.nextgenmanager.nextgenmanager.packaging.repository.PackageLineRepository;
import com.nextgenmanager.nextgenmanager.packaging.repository.PackingSlipRepository;
import com.nextgenmanager.nextgenmanager.packaging.service.PackagingGateService;
import com.nextgenmanager.nextgenmanager.packaging.service.PackingSlipNumberGenerator;
import com.nextgenmanager.nextgenmanager.packaging.service.PackingSlipServiceImpl;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Packing records what physically went into a box. The rules worth testing are the ones stopping a
 * box from claiming more than was picked, or claiming the same physical unit twice — both of which
 * are easy to get wrong because the guards have to reason about rows that are not on the database
 * yet.
 */
@ExtendWith(MockitoExtension.class)
class PackingSlipServiceImplTest {

    @Mock private PackingSlipRepository packingSlipRepository;
    @Mock private PackageBoxRepository packageBoxRepository;
    @Mock private PackageLineRepository packageLineRepository;
    @Mock private PickListRepository pickListRepository;
    @Mock private InventoryInstanceRepository inventoryInstanceRepository;
    @Mock private PackagingGateService packagingGateService;
    @Mock private PackingSlipNumberGenerator numberGenerator;

    @InjectMocks private PackingSlipServiceImpl service;

    private static final int PLAIN_ITEM_ID = 77;
    private static final int TRACKED_ITEM_ID = 88;

    private InventoryItem plainItem, trackedItem;
    private PickList pick;
    private PickListLine plainLine, trackedLine;
    private PackingSlip slip;

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

    private PickListLine pickLine(Long id, InventoryItem item, String picked) {
        PickListLine l = new PickListLine();
        l.setId(id);
        l.setPickList(pick);
        l.setInventoryItem(item);
        l.setQuantityToPick(new BigDecimal(picked));
        l.setQuantityPicked(new BigDecimal(picked));
        return l;
    }

    private InventoryInstance instance(long id, InventoryItem item, PickListLine on, String qty) {
        InventoryInstance inst = new InventoryInstance();
        inst.setId(id);
        inst.setInventoryItem(item);
        inst.setQuantity(new BigDecimal(qty));
        inst.setPickListLine(on);
        return inst;
    }

    @BeforeEach
    void setUp() {
        plainItem   = item(PLAIN_ITEM_ID, "BOLT-1", false);
        trackedItem = item(TRACKED_ITEM_ID, "VALVE-1", true);

        Warehouse main = new Warehouse();
        main.setId(1L);
        main.setCode("MAIN");

        SalesOrder so = new SalesOrder();
        so.setId(10L);
        so.setOrderNumber("SO/2026-27/0001");

        pick = new PickList();
        pick.setId(5L);
        pick.setPickNumber("PK/0001");
        pick.setSalesOrder(so);
        pick.setWarehouse(main);
        pick.setStatus(PickListStatus.PICKED);

        plainLine   = pickLine(100L, plainItem, "10");
        trackedLine = pickLine(200L, trackedItem, "10");
        pick.getLines().add(plainLine);
        pick.getLines().add(trackedLine);

        slip = new PackingSlip();
        slip.setId(1L);
        slip.setSlipNumber("PS/0001");
        slip.setSalesOrder(so);
        slip.setPickList(pick);
        slip.setStatus(PackingSlipStatus.DRAFT);

        lenient().when(packingSlipRepository.findLiveById(1L)).thenReturn(Optional.of(slip));
        lenient().when(packageBoxRepository.maxBoxNumber(any())).thenReturn(0);
        lenient().when(packageBoxRepository.save(any(PackageBox.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(packageLineRepository.sumAlreadyPackaged(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(inventoryInstanceRepository.findByPackageLineId(any())).thenReturn(List.of());
    }

    private PackageBoxCreateRequest box(PackageLineRequest... lines) {
        return new PackageBoxCreateRequest("CARTON", null, null, null, null, null, null, List.of(lines));
    }

    // ─── What was picked is the ceiling, across every route to it ─────────────

    @Test
    void packsUpToWhatWasPicked() {
        var dto = service.addBox(1L, box(new PackageLineRequest(100L, null, new BigDecimal("10"), null)));

        assertThat(dto.boxNumber()).isEqualTo(1);
        assertThat(dto.lines()).singleElement()
                .satisfies(l -> assertThat(l.quantity()).isEqualByComparingTo("10"));
    }

    @Test
    void refusesMoreThanWasPicked() {
        assertThatThrownBy(() -> service.addBox(1L,
                box(new PackageLineRequest(100L, null, new BigDecimal("11"), null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 10 of 10 picked is still unpacked");
    }

    @Test
    void countsWhatEarlierBoxesAlreadyTook() {
        when(packageLineRepository.sumAlreadyPackaged(100L)).thenReturn(new BigDecimal("7"));

        assertThatThrownBy(() -> service.addBox(1L,
                box(new PackageLineRequest(100L, null, new BigDecimal("4"), null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 3 of 10");
    }

    /**
     * The sibling lines of one request are not on the database yet, so the per-line query cannot
     * see them. Without the running tally each line would be measured against the full remainder.
     */
    @Test
    void countsWhatEarlierLinesOfTheSameRequestTook() {
        assertThatThrownBy(() -> service.addBox(1L, box(
                new PackageLineRequest(100L, null, new BigDecimal("6"), null),
                new PackageLineRequest(100L, null, new BigDecimal("6"), null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 4 of 10");
    }

    /**
     * Naming the item rather than the line is a convenience, not a way past the quantity check —
     * the line it resolves to carries the same ceiling.
     */
    @Test
    void itemNamedWithoutItsLineIsStillHeldToWhatWasPicked() {
        assertThatThrownBy(() -> service.addBox(1L,
                box(new PackageLineRequest(null, PLAIN_ITEM_ID, new BigDecimal("11"), null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 10 of 10 picked is still unpacked");
    }

    @Test
    void itemNamedWithoutItsLineIsRecordedAgainstThatLine() {
        var dto = service.addBox(1L,
                box(new PackageLineRequest(null, PLAIN_ITEM_ID, new BigDecimal("4"), null)));

        // Without this the line would be invisible to every later "how much is left" check.
        assertThat(dto.lines()).singleElement()
                .satisfies(l -> assertThat(l.pickListLineId()).isEqualTo(100L));
    }

    @Test
    void refusesAnItemThatIsOnMoreThanOnePickLine() {
        pick.getLines().add(pickLine(300L, plainItem, "5"));

        assertThatThrownBy(() -> service.addBox(1L,
                box(new PackageLineRequest(null, PLAIN_ITEM_ID, new BigDecimal("1"), null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is on more than one line");
    }

    @Test
    void refusesALineNamingNeitherPickLineNorItem() {
        assertThatThrownBy(() -> service.addBox(1L,
                box(new PackageLineRequest(null, null, new BigDecimal("1"), null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name either a pick line or an item");
    }

    // ─── One physical unit goes into one box ──────────────────────────────────

    /**
     * Nothing is written until the whole list resolves, so the "already packed into box N" guard
     * cannot catch a repeat inside one list. Everything else about this request is legitimate —
     * 6 is within the 10 picked, and the named instances "add up" to exactly 6 — so without the
     * de-duplication one 3-unit instance would be packed as 6 units and the box would be accepted.
     */
    @Test
    void refusesTheSameInstanceNamedTwiceOnOneLine() {
        InventoryInstance three = instance(900L, trackedItem, trackedLine, "3");
        when(inventoryInstanceRepository.findById(900L)).thenReturn(Optional.of(three));

        assertThatThrownBy(() -> service.addBox(1L, box(new PackageLineRequest(
                200L, null, new BigDecimal("6"), List.of(900L, 900L)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("named twice");
    }

    @Test
    void refusesInstancesThatDoNotAddUpToThePackedQuantity() {
        InventoryInstance six = instance(900L, trackedItem, trackedLine, "6");
        when(inventoryInstanceRepository.findById(900L)).thenReturn(Optional.of(six));

        assertThatThrownBy(() -> service.addBox(1L, box(new PackageLineRequest(
                200L, null, new BigDecimal("7"), List.of(900L)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add up to 6");
    }

    @Test
    void refusesATrackedItemWithNoInstancesNamed() {
        assertThatThrownBy(() -> service.addBox(1L,
                box(new PackageLineRequest(200L, null, new BigDecimal("6"), null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch or serial tracked");
    }

    @Test
    void refusesAnInstanceThatWasNotPickedOnThisPick() {
        PickList other = new PickList();
        other.setId(99L);
        other.setPickNumber("PK/0099");
        PickListLine otherLine = new PickListLine();
        otherLine.setId(999L);
        otherLine.setPickList(other);

        InventoryInstance stray = instance(901L, trackedItem, otherLine, "6");
        when(inventoryInstanceRepository.findById(901L)).thenReturn(Optional.of(stray));

        assertThatThrownBy(() -> service.addBox(1L, box(new PackageLineRequest(
                200L, null, new BigDecimal("6"), List.of(901L)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not picked on PK/0001");
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Test
    void onlyADraftSlipTakesBoxes() {
        slip.setStatus(PackingSlipStatus.PACKED);

        assertThatThrownBy(() -> service.addBox(1L,
                box(new PackageLineRequest(100L, null, BigDecimal.ONE, null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("it is PACKED, not DRAFT");
    }

    @Test
    void aSlipWithNoBoxesCannotBePacked() {
        assertThatThrownBy(() -> service.pack(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no boxes yet");
    }

    @Test
    void closingRunsTheQualityGate() {
        slip.setStatus(PackingSlipStatus.PACKED);
        when(packingSlipRepository.save(any(PackingSlip.class))).thenAnswer(i -> i.getArgument(0));

        service.close(1L);

        // The gate decides; this only proves close() asks it rather than closing regardless.
        org.mockito.Mockito.verify(packagingGateService).assertClosingAllowed(slip);
        assertThat(slip.getStatus()).isEqualTo(PackingSlipStatus.CLOSED);
    }

    @Test
    void aClosedSlipCannotBeCancelled() {
        slip.setStatus(PackingSlipStatus.CLOSED);

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is closed");
    }
}
