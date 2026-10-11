package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ItemVendorPriceRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.WorkCenterRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderDto;
import com.nextgenmanager.nextgenmanager.purchase.model.ShipToKind;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.ConvertToPoRequestDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.PurchaseRequisitionCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.requisition.mapper.PurchaseRequisitionMapper;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisition;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionItem;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionPriority;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionSource;
import com.nextgenmanager.nextgenmanager.purchase.requisition.repository.PurchaseRequisitionRepository;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionApprovalService;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionNumberGenerator;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A requisition names the warehouse its goods are for, and the purchase order raised from it is
 * addressed to that plant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequisitionDeliverToTest {

    @Mock private PurchaseRequisitionRepository prRepo;
    @Mock private ContactRepository contactRepo;
    @Mock private InventoryItemRepository itemRepo;
    @Mock private WorkOrderRepository workOrderRepo;
    @Mock private WorkOrderMaterialRepository workOrderMaterialRepo;
    @Mock private WorkCenterRepository workCenterRepo;
    @Mock private ItemVendorPriceRepository vendorPriceRepo;
    @Mock private EntityManager em;
    @Mock private PurchaseRequisitionNumberGenerator numberGen;
    @Mock private PurchaseRequisitionApprovalService approvalService;
    @Mock private PurchaseRequisitionMapper mapper;
    @Mock private PurchaseOrderService purchaseOrderService;
    @Mock private WarehouseService warehouseService;

    private PurchaseRequisitionServiceImpl service;
    private Warehouse main;
    private Warehouse plantTwo;

    @BeforeEach
    void setUp() {
        service = new PurchaseRequisitionServiceImpl(prRepo, contactRepo, itemRepo, workOrderRepo,
                workOrderMaterialRepo, workCenterRepo, vendorPriceRepo, em, numberGen, approvalService,
                mapper, purchaseOrderService, warehouseService);

        main = warehouse(1L, "MAIN", true);
        plantTwo = warehouse(2L, "PLANT-2", false);
        when(warehouseService.resolveByCodeOrDefault(null)).thenReturn(main);
        when(warehouseService.resolveByCodeOrDefault("PLANT-2")).thenReturn(plantTwo);

        when(numberGen.next()).thenReturn("PR/2026-27/0001");
        when(prRepo.save(any())).thenAnswer(call -> call.getArgument(0));
        PurchaseOrderDto raised = mock(PurchaseOrderDto.class);
        when(raised.id()).thenReturn(500L);
        when(purchaseOrderService.create(any())).thenReturn(raised);
    }

    @Test
    void aRequisitionKeyedWithNoWarehouse_isForTheDefaultOne_saidOutLoud() {
        service.create(newRequisition(null));

        assertThat(saved().getDeliverToWarehouse()).isSameAs(main);
    }

    @Test
    void aRequisition_keepsTheWarehouseItWasRaisedFor() {
        service.create(newRequisition("PLANT-2"));

        assertThat(saved().getDeliverToWarehouse()).isSameAs(plantTwo);
    }

    @Test
    void theOrderRaisedFromARequisitionForAnotherPlant_shipsToThatPlant() {
        approvedRequisitionFor(plantTwo);

        service.convertToPurchaseOrder(9L, new ConvertToPoRequestDto(3, null, null, null));

        PurchaseOrderCreateDto order = raisedOrder();
        assertThat(order.shipToKind()).isEqualTo(ShipToKind.PLANT);
        assertThat(order.shipToWarehouseId()).isEqualTo(2L);
    }

    @Test
    void theOrderRaisedFromARequisitionForTheDefaultWarehouse_staysAddressedToTheCompany() {
        // An order with no ship-to is delivered to the registered address and received into the
        // default warehouse already. Naming that warehouse would print its own address in place of
        // the registered one, and a single-site company has often never filled that in.
        approvedRequisitionFor(main);

        service.convertToPurchaseOrder(9L, new ConvertToPoRequestDto(3, null, null, null));

        PurchaseOrderCreateDto order = raisedOrder();
        assertThat(order.shipToKind()).isNull();
        assertThat(order.shipToWarehouseId()).isNull();
    }

    @Test
    void anOlderRequisitionWithNoWarehouse_convertsExactlyAsItUsedTo() {
        approvedRequisitionFor(null);

        service.convertToPurchaseOrder(9L, new ConvertToPoRequestDto(3, null, null, null));

        PurchaseOrderCreateDto order = raisedOrder();
        assertThat(order.shipToKind()).isNull();
        assertThat(order.shipToWarehouseId()).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────

    private static PurchaseRequisitionCreateDto newRequisition(String deliverToWarehouseCode) {
        return new PurchaseRequisitionCreateDto(new Date(), null, "stores", null, null,
                PurchaseRequisitionPriority.NORMAL, PurchaseRequisitionSource.MANUAL, null, null,
                List.of(), null, deliverToWarehouseCode);
    }

    private PurchaseRequisition saved() {
        ArgumentCaptor<PurchaseRequisition> saved = ArgumentCaptor.forClass(PurchaseRequisition.class);
        verify(prRepo).save(saved.capture());
        return saved.getValue();
    }

    private PurchaseOrderCreateDto raisedOrder() {
        ArgumentCaptor<PurchaseOrderCreateDto> raised = ArgumentCaptor.forClass(PurchaseOrderCreateDto.class);
        verify(purchaseOrderService).create(raised.capture());
        return raised.getValue();
    }

    private void approvedRequisitionFor(Warehouse deliverTo) {
        InventoryItem bearing = new InventoryItem();
        bearing.setInventoryItemId(40);

        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setId(9L);
        pr.setPrNumber("PR/2026-27/0009");
        pr.setApprovalStatus(PurchaseRequisitionApprovalStatus.APPROVED);
        pr.setDeliverToWarehouse(deliverTo);

        PurchaseRequisitionItem line = new PurchaseRequisitionItem();
        line.setId(1L);
        line.setRequisition(pr);
        line.setItem(bearing);
        line.setQuantity(4);
        line.setEstimatedUnitPrice(new BigDecimal("120"));
        pr.setItems(new ArrayList<>(List.of(line)));

        when(prRepo.findByIdAndDeletedDateIsNull(9L)).thenReturn(Optional.of(pr));
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
