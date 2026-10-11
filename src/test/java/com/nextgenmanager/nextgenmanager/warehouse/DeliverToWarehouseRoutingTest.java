package com.nextgenmanager.nextgenmanager.warehouse;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstanceStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryProcurementOrder;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryProcurementOrderRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceServiceImp;
import com.nextgenmanager.nextgenmanager.Inventory.service.ProcurementRoutingService;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.routing.Routing;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.routing.RoutingRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderDTO;
import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderRequestDTO;
import com.nextgenmanager.nextgenmanager.production.service.workorder.WorkOrderService;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionService;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The warehouse a shortfall is wanted in is decided when the need is raised and carried by
 * whichever document the need becomes. These follow it from the sales order to the need, and from
 * the need to the work order or the requisition.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliverToWarehouseRoutingTest {

    @Mock private InventoryRequestRepository inventoryRequestRepository;
    @Mock private InventoryProcurementOrderRepository procurementOrderRepository;
    @Mock private SalesOrderRepository salesOrderRepository;
    @Mock private WorkOrderService workOrderService;
    @Mock private PurchaseRequisitionService purchaseRequisitionService;
    @Mock private BomRepository bomRepository;
    @Mock private RoutingRepository routingRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryInstanceRepository inventoryInstanceRepository;

    @InjectMocks private ProcurementRoutingService routing;
    @InjectMocks private InventoryInstanceServiceImp instances;

    private Warehouse main;
    private Warehouse plantTwo;
    private SalesOrder salesOrder;

    @BeforeEach
    void setUp() {
        main = warehouse(1L, "MAIN", true);
        plantTwo = warehouse(2L, "PLANT-2", false);
        when(warehouseService.resolveDefaultWarehouse()).thenReturn(main);
        when(warehouseService.resolveByCodeOrDefault("PLANT-2")).thenReturn(plantTwo);

        salesOrder = new SalesOrder();
        salesOrder.setId(1L);
        salesOrder.setOrderNumber("SO/2026-27/0001");
        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(salesOrder));

        WorkOrderDTO raised = new WorkOrderDTO();
        raised.setId(55);
        when(workOrderService.addWorkOrder(any())).thenReturn(raised);
        PurchaseRequisitionDto requisition = mock(PurchaseRequisitionDto.class);
        when(requisition.id()).thenReturn(77L);
        when(purchaseRequisitionService.create(any())).thenReturn(requisition);
    }

    // ── sales order → need ────────────────────────────────────────────────────────────────

    @Test
    void aShortfallForASalesOrder_isWantedInTheWarehouseThatOrderIsDispatchedFrom() {
        salesOrder.setDispatchWarehouse(plantTwo);
        InventoryItem valve = item(901, false, true);
        stubRequestInstance(valve);

        instances.requestInstance(valve, 3, InventoryRequestSource.SALES_ORDER, 1L, "planner", "MTO");

        InventoryProcurementOrder need = savedNeed();
        assertThat(need.getDeliverToWarehouse()).isSameAs(plantTwo);
        // The placeholders for the goods on order sit where the goods are going to arrive.
        assertThat(need.getPendingInventoryList())
                .hasSize(3)
                .allMatch(i -> i.getInventoryInstanceStatus() == InventoryInstanceStatus.PENDING)
                .allMatch(i -> i.getWarehouse() == plantTwo);
    }

    @Test
    void aShortfallForASalesOrderThatNamesNoWarehouse_isWantedInTheDefaultOne() {
        InventoryItem valve = item(901, false, true);
        stubRequestInstance(valve);

        instances.requestInstance(valve, 3, InventoryRequestSource.SALES_ORDER, 1L, "planner", "MTO");

        // Still decided, never left blank: the need says MAIN rather than nothing.
        assertThat(savedNeed().getDeliverToWarehouse()).isSameAs(main);
    }

    // ── need → work order ─────────────────────────────────────────────────────────────────

    @Test
    void aNeedThatIsMade_tellsItsWorkOrderWhereTheGoodsAreWanted() {
        InventoryProcurementOrder need = needFor(item(901, true, false), plantTwo);
        stubBomAndRouting(901);

        routing.routeForSalesOrder(1L);

        ArgumentCaptor<WorkOrderRequestDTO> raised = ArgumentCaptor.forClass(WorkOrderRequestDTO.class);
        verify(workOrderService).addWorkOrder(raised.capture());
        assertThat(raised.getValue().getProduceIntoWarehouseCode()).isEqualTo("PLANT-2");
        assertThat(need.getOrderId()).isEqualTo(55L);
    }

    // ── need → requisition ────────────────────────────────────────────────────────────────

    @Test
    void aNeedThatIsBought_addressesItsRequisitionToTheSameWarehouse() {
        needFor(item(902, false, true), plantTwo);

        routing.routeForSalesOrder(1L);

        assertThat(raisedRequisition().deliverToWarehouseCode()).isEqualTo("PLANT-2");
    }

    @Test
    void aNeedRaisedBeforeWarehousesWereCarried_isForTheDefaultWarehouse() {
        needFor(item(902, false, true), null);

        routing.routeForSalesOrder(1L);

        assertThat(raisedRequisition().deliverToWarehouseCode()).isEqualTo("MAIN");
    }

    // ── the planner can still change it ───────────────────────────────────────────────────

    @Test
    void thePlanner_canSendAnUndecidedNeedSomewhereElseWhenDeciding() {
        InventoryProcurementOrder need = needFor(item(903, true, true), main);
        when(procurementOrderRepository.findById(100L)).thenReturn(Optional.of(need));

        routing.decidePurchase(100L, "PLANT-2");

        assertThat(need.getDeliverToWarehouse()).isSameAs(plantTwo);
        assertThat(raisedRequisition().deliverToWarehouseCode()).isEqualTo("PLANT-2");
    }

    @Test
    void decidingWithoutNamingAWarehouse_keepsTheOneTheNeedWasRaisedWith() {
        InventoryProcurementOrder need = needFor(item(903, true, true), plantTwo);
        when(procurementOrderRepository.findById(100L)).thenReturn(Optional.of(need));
        stubBomAndRouting(903);

        routing.decideWorkOrder(100L, " ");

        ArgumentCaptor<WorkOrderRequestDTO> raised = ArgumentCaptor.forClass(WorkOrderRequestDTO.class);
        verify(workOrderService).addWorkOrder(raised.capture());
        assertThat(need.getDeliverToWarehouse()).isSameAs(plantTwo);
        assertThat(raised.getValue().getProduceIntoWarehouseCode()).isEqualTo("PLANT-2");
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────

    private PurchaseRequisitionCreateDto raisedRequisition() {
        ArgumentCaptor<PurchaseRequisitionCreateDto> raised = ArgumentCaptor.forClass(PurchaseRequisitionCreateDto.class);
        verify(purchaseRequisitionService).create(raised.capture());
        return raised.getValue();
    }

    private InventoryProcurementOrder savedNeed() {
        ArgumentCaptor<InventoryProcurementOrder> saved = ArgumentCaptor.forClass(InventoryProcurementOrder.class);
        verify(procurementOrderRepository).save(saved.capture());
        return saved.getValue();
    }

    /** Nothing on the shelf, so the whole request becomes a shortfall. */
    private void stubRequestInstance(InventoryItem item) {
        when(inventoryItemRepository.findByActiveId(item.getInventoryItemId())).thenReturn(item);
        when(inventoryRequestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(inventoryInstanceRepository.saveAll(any())).thenAnswer(call -> call.getArgument(0));
    }

    /** An undecided need of five units for the sales order, wanted in the given warehouse. */
    private InventoryProcurementOrder needFor(InventoryItem item, Warehouse wantedIn) {
        InventoryRequest request = new InventoryRequest();
        request.setId(10L);
        request.setRequestSource(InventoryRequestSource.SALES_ORDER);
        request.setSourceId(1L);

        InventoryInstance shortfall = new InventoryInstance();
        shortfall.setQuantity(new BigDecimal("5"));

        InventoryProcurementOrder need = new InventoryProcurementOrder();
        need.setId(100L);
        need.setInventoryRequest(request);
        need.setInventoryItem(item);
        need.setPendingInventoryList(List.of(shortfall));
        need.setDeliverToWarehouse(wantedIn);

        when(inventoryRequestRepository.findBySourceIdAndRequestSource(1L, InventoryRequestSource.SALES_ORDER))
                .thenReturn(List.of(request));
        when(procurementOrderRepository.findByInventoryRequestId(10L)).thenReturn(List.of(need));
        return need;
    }

    private void stubBomAndRouting(int itemId) {
        Bom bom = new Bom();
        bom.setId(7);
        Routing route = new Routing();
        route.setId(9L);
        when(bomRepository.findActiveBomWithPositionsByParentItemId(itemId)).thenReturn(Optional.of(bom));
        when(routingRepository.findByBomId(7)).thenReturn(Optional.of(route));
    }

    private static InventoryItem item(int id, boolean manufactured, boolean purchased) {
        ProductInventorySettings settings = new ProductInventorySettings();
        settings.setManufactured(manufactured);
        settings.setPurchased(purchased);
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(id);
        item.setItemCode("ITEM-" + id);
        item.setName("Item " + id);
        item.setUom(UOM.NOS);
        item.setProductInventorySettings(settings);
        return item;
    }

    private static Warehouse warehouse(Long id, String code, boolean isDefault) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode(code);
        w.setName(code);
        w.setDefault(isDefault);
        return w;
    }
}
