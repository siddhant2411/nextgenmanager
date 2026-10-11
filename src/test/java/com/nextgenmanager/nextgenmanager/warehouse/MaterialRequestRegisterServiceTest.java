package com.nextgenmanager.nextgenmanager.warehouse;

import com.nextgenmanager.nextgenmanager.Inventory.dto.MaterialRequestRowDto;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryBookingApproval;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstanceStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryBookingApprovalRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.MaterialRequestRegisterService;
import com.nextgenmanager.nextgenmanager.Inventory.service.MaterialRequestRegisterService.SourceFilter;
import com.nextgenmanager.nextgenmanager.Inventory.service.MaterialRequestRegisterService.StatusFilter;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.model.UOM;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the stores' register says about a request. The query itself is the database's to answer;
 * these cover how a request that came back is turned into a row -- its quantities, whether the
 * stores can act on it, and who decided it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterialRequestRegisterServiceTest {

    @Mock private InventoryRequestRepository requestRepository;
    @Mock private InventoryBookingApprovalRepository bookingApprovalRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private SalesOrderRepository salesOrderRepository;
    @Mock private WarehouseService warehouseService;

    @InjectMocks private MaterialRequestRegisterService register;

    @BeforeEach
    void setUp() {
        Warehouse main = new Warehouse();
        main.setCode("MAIN");
        when(warehouseService.resolveDefaultWarehouse()).thenReturn(main);
    }

    @Test
    void aWorkOrdersRequest_showsWhatWasAskedIssuedAndStillOwed() {
        WorkOrder wo = new WorkOrder();
        wo.setId(7);
        wo.setWorkOrderNumber("WO/2026-27/0007");
        when(workOrderRepository.findAllById(anyIterable())).thenReturn(List.of(wo));

        InventoryRequest request = request(1L, InventoryRequestSource.WORK_ORDER, 7L, InventoryApprovalStatus.PARTIAL, 50);
        request.setRequestedQuantity(new BigDecimal("100"));
        request.setApprovedQuantity(new BigDecimal("40"));

        MaterialRequestRowDto row = only(request);

        assertThat(row.sourceNumber()).isEqualTo("WO/2026-27/0007");
        assertThat(row.requestedQty()).isEqualByComparingTo("100");
        assertThat(row.issuedQty()).isEqualByComparingTo("40");
        assertThat(row.remainingQty()).isEqualByComparingTo("60");
        // 50 on the shelf will not cover the 60 still owed.
        assertThat(row.stockShort()).isTrue();
        assertThat(row.actionable()).isTrue();
        // No work centre names a store, so the material is wanted in the default warehouse.
        assertThat(row.warehouseCode()).isEqualTo("MAIN");
    }

    @Test
    void aSalesOrdersReservation_isListedButIsNotTheStoresToDecide() {
        Warehouse plantTwo = new Warehouse();
        plantTwo.setCode("PLANT-2");
        SalesOrder so = new SalesOrder();
        so.setId(3L);
        so.setOrderNumber("SO/2026-27/0003");
        so.setDispatchWarehouse(plantTwo);
        when(salesOrderRepository.findAllById(anyIterable())).thenReturn(List.of(so));

        InventoryRequest request = request(2L, InventoryRequestSource.SALES_ORDER, 3L, InventoryApprovalStatus.PENDING, 0);
        request.setRequestedInstances(List.of(
                unit("6", InventoryInstanceStatus.REQUESTED),
                unit("4", InventoryInstanceStatus.PENDING)));

        MaterialRequestRowDto row = only(request);

        assertThat(row.sourceNumber()).isEqualTo("SO/2026-27/0003");
        assertThat(row.warehouseCode()).isEqualTo("PLANT-2");
        // Ten wanted: six reserved off the shelf, four not here yet.
        assertThat(row.requestedQty()).isEqualByComparingTo("10");
        assertThat(row.issuedQty()).isEqualByComparingTo("6");
        assertThat(row.remainingQty()).isEqualByComparingTo("4");
        assertThat(row.actionable()).isFalse();
        assertThat(row.stockShort()).isFalse();
    }

    @Test
    void aDecidedRequest_saysWhoDecidedItAndWhy() {
        InventoryRequest request = request(4L, InventoryRequestSource.WORK_ORDER, 7L, InventoryApprovalStatus.REJECTED, 0);
        request.setRequestedQuantity(new BigDecimal("5"));
        request.setApprovedBy("storekeeper");
        request.setApprovedDate(new Date());
        request.setRejectionReason("Quality Hold");

        MaterialRequestRowDto row = only(request);

        assertThat(row.decidedBy()).isEqualTo("storekeeper");
        assertThat(row.rejectionReason()).isEqualTo("Quality Hold");
        assertThat(row.actionable()).isFalse();
    }

    @Test
    void anOlderRequestDecidedBeforeDecisionsWereKeptOnIt_isReadFromItsApprovalRecord() {
        InventoryBookingApproval recorded = new InventoryBookingApproval();
        recorded.setApprovedBy("old.hand");
        recorded.setApprovalDate(new Date());
        recorded.setApprovalRemarks("Rejected via Panel by old.hand");
        when(bookingApprovalRepository.findFirstByInventoryRequestIdOrderByApprovalDateDesc(5L))
                .thenReturn(Optional.of(recorded));

        InventoryRequest request = request(5L, InventoryRequestSource.MANUAL, null, InventoryApprovalStatus.REJECTED, 0);
        request.setRequestedInstances(List.of(unit("2", InventoryInstanceStatus.AVAILABLE)));

        MaterialRequestRowDto row = only(request);

        assertThat(row.decidedBy()).isEqualTo("old.hand");
        assertThat(row.rejectionReason()).isEqualTo("Rejected via Panel by old.hand");
    }

    @Test
    void theQueue_isWorkedOldestFirst_andHistoryIsReadNewestFirst() {
        when(requestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        register.search(StatusFilter.OPEN, SourceFilter.STORES, null, null, null, 0, 20);
        register.search(StatusFilter.REJECTED, SourceFilter.STORES, null, null, null, 0, 500);

        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(requestRepository, org.mockito.Mockito.times(2)).findAll(any(Specification.class), asked.capture());
        assertThat(asked.getAllValues().get(0).getSort().getOrderFor("requestedDate").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(asked.getAllValues().get(1).getSort().getOrderFor("requestedDate").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        // A caller cannot ask for the whole table in one page.
        assertThat(asked.getAllValues().get(1).getPageSize()).isEqualTo(100);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────

    private MaterialRequestRowDto only(InventoryRequest request) {
        when(requestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(request)));
        return register.search(StatusFilter.ALL, SourceFilter.STORES, null, null, null, 0, 20)
                .getContent().get(0);
    }

    private static InventoryRequest request(Long id, InventoryRequestSource source, Long sourceId,
                                            InventoryApprovalStatus status, double available) {
        ProductInventorySettings settings = new ProductInventorySettings();
        settings.setAvailableQuantity(available);
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(40);
        item.setItemCode("BRG-6204");
        item.setName("Bearing 6204");
        item.setUom(UOM.NOS);
        item.setProductInventorySettings(settings);

        InventoryRequest request = new InventoryRequest();
        request.setId(id);
        request.setRequestSource(source);
        request.setSourceId(sourceId);
        request.setApprovalStatus(status);
        request.setInventoryItem(item);
        request.setRequestedDate(new Date());
        return request;
    }

    private static InventoryInstance unit(String quantity, InventoryInstanceStatus status) {
        InventoryInstance unit = new InventoryInstance();
        unit.setQuantity(new BigDecimal(quantity));
        unit.setInventoryInstanceStatus(status);
        return unit;
    }
}
