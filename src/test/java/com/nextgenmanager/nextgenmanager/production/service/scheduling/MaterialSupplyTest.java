package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.service.scheduling.engine.MaterialLedger;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisition;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionItem;
import com.nextgenmanager.nextgenmanager.purchase.requisition.repository.PurchaseRequisitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterialSupplyTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 8, 0);

    @Mock
    private WorkOrderMaterialRepository workOrderMaterialRepository;
    @Mock
    private InventoryRequestRepository inventoryRequestRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private PurchaseRequisitionRepository purchaseRequisitionRepository;

    @InjectMocks
    private MaterialSupply supply;

    private InventoryItem steel;
    private WorkOrder order;

    @BeforeEach
    void setUp() {
        when(purchaseOrderRepository.findLinesForItems(anyCollection(), anyList())).thenReturn(List.of());
        when(purchaseRequisitionRepository.findOpenLinesForItems(anyCollection(), any(), any(), anyList())).thenReturn(List.of());
        when(workOrderMaterialRepository.findScheduledDemandForItems(anyCollection(), anyList())).thenReturn(List.of());
        when(inventoryRequestRepository.findAllById(any())).thenReturn(List.of());
        steel = item(7, "RM-STEEL", 20, 0);
        order = order(10);
    }

    @Test
    void whatIsIssuedOrReservedForAMaterialIsNotNeededAgain() {
        WorkOrderMaterial nothingYet = material(1L, order, steel, 100, 0);
        WorkOrderMaterial partlyIssued = material(2L, order, steel, 100, 30);
        WorkOrderMaterial approved = material(3L, order, steel, 100, 0);
        approved.setInventoryRequestId(55L);
        InventoryRequest request = new InventoryRequest();
        request.setId(55L);
        request.setApprovalStatus(InventoryApprovalStatus.APPROVED);
        request.setApprovedQuantity(BigDecimal.valueOf(100));
        when(inventoryRequestRepository.findAllById(any())).thenReturn(List.of(request));

        List<MaterialSupply.Need> needs = supply.needs(List.of(nothingYet, partlyIssued, approved));

        assertThat(needs).extracting(MaterialSupply.Need::quantity).containsExactly(100.0, 70.0);
    }

    @Test
    void stockThenOpenOrdersThenRequisitionsThenLeadTime() {
        steel.getProductInventorySettings().setLeadTime(10);
        WorkOrderMaterial need = material(1L, order, steel, 1000, 0);

        PurchaseOrderItem poLine = poLine(steel, "PO/0031", NOW.plusDays(3), 100, 40);
        when(purchaseOrderRepository.findLinesForItems(anyCollection(), anyList())).thenReturn(List.of(poLine));
        PurchaseRequisitionItem prLine = prLine(steel, "PR/0005", 50);
        when(purchaseRequisitionRepository.findOpenLinesForItems(anyCollection(), any(), any(), anyList())).thenReturn(List.of(prLine));

        MaterialLedger ledger = supply.load(List.of(order), Map.of(10, List.of(need)), NOW);

        assertThat(ledger.allocate(7, 20).source()).isEqualTo("stock");
        assertThat(ledger.allocate(7, 60).source()).isEqualTo("PO/0031");          // 100 ordered, 40 received
        assertThat(ledger.allocate(7, 50).source()).isEqualTo("requisition PR/0005");
        MaterialLedger.Allocation beyond = ledger.allocate(7, 1);
        assertThat(beyond.source()).isEqualTo("lead time 10 days");
        assertThat(beyond.readyAt()).isEqualTo(NOW.plusDays(10));
    }

    @Test
    void anOverduePurchaseOrderCanComeAnyTime() {
        WorkOrderMaterial need = material(1L, order, item(7, "RM-STEEL", 0, 0), 10, 0);
        PurchaseOrderItem late = poLine(need.getComponent(), "PO/0029", NOW.minusDays(2), 50, 0);
        when(purchaseOrderRepository.findLinesForItems(anyCollection(), anyList())).thenReturn(List.of(late));

        MaterialLedger ledger = supply.load(List.of(order), Map.of(10, List.of(need)), NOW);

        MaterialLedger.Allocation allocation = ledger.allocate(7, 10);
        assertThat(allocation.readyAt()).isEqualTo(NOW);
        assertThat(allocation.source()).isEqualTo("PO/0029, overdue");
    }

    @Test
    void negativeStockCountsAsNone() {
        WorkOrderMaterial need = material(1L, order, item(7, "RM-STEEL", -15, 0), 10, 0);

        MaterialLedger ledger = supply.load(List.of(order), Map.of(10, List.of(need)), NOW);

        assertThat(ledger.allocate(7, 1).dated()).isFalse();
    }

    @Test
    void otherScheduledOrdersClaimFirstButTheOrdersBeingPlannedDoNot() {
        WorkOrderMaterial need = material(1L, order, steel, 10, 0);
        WorkOrder earlier = order(20);
        earlier.setPlannedStartDate(date(NOW.plusDays(1)));
        WorkOrderMaterial earlierClaim = material(2L, earlier, steel, 15, 0);
        WorkOrderMaterial ownRow = material(3L, order, steel, 10, 0);
        when(workOrderMaterialRepository.findScheduledDemandForItems(anyCollection(), anyList()))
                .thenReturn(List.of(earlierClaim, ownRow));

        MaterialLedger ledger = supply.load(List.of(order), Map.of(10, List.of(need)), NOW);

        // 20 in stock, 15 already promised to the other order: 5 left now.
        assertThat(ledger.allocate(7, 5).readyAt()).isEqualTo(NOW);
        assertThat(ledger.allocate(7, 1).dated()).isFalse();
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────

    private static InventoryItem item(int id, String code, double available, double leadTimeDays) {
        ProductInventorySettings settings = new ProductInventorySettings();
        settings.setAvailableQuantity(available);
        settings.setLeadTime(leadTimeDays);
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId(id);
        item.setItemCode(code);
        item.setProductInventorySettings(settings);
        return item;
    }

    private static WorkOrder order(int id) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setWorkOrderNumber("WO-" + id);
        wo.setWorkOrderStatus(WorkOrderStatus.SCHEDULED);
        return wo;
    }

    private static WorkOrderMaterial material(Long id, WorkOrder wo, InventoryItem item, double required, double issued) {
        WorkOrderMaterial m = new WorkOrderMaterial();
        m.setId(id);
        m.setWorkOrder(wo);
        m.setComponent(item);
        m.setNetRequiredQuantity(BigDecimal.valueOf(required));
        m.setIssuedQuantity(BigDecimal.valueOf(issued));
        return m;
    }

    private static PurchaseOrderItem poLine(InventoryItem item, String number, LocalDateTime expected, double ordered, double received) {
        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseOrderNumber(number);
        po.setExpectedDeliveryDate(date(expected));
        PurchaseOrderItem line = new PurchaseOrderItem();
        line.setPurchaseOrder(po);
        line.setItem(item);
        line.setQuantityOrdered(ordered);
        line.setQuantityReceived(received);
        return line;
    }

    private static PurchaseRequisitionItem prLine(InventoryItem item, String number, double quantity) {
        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPrNumber(number);
        PurchaseRequisitionItem line = new PurchaseRequisitionItem();
        line.setRequisition(pr);
        line.setItem(item);
        line.setQuantity(quantity);
        return line;
    }

    private static Date date(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(IST).toInstant());
    }
}
