package com.nextgenmanager.nextgenmanager.warehouse;

import com.nextgenmanager.nextgenmanager.Inventory.exception.NegativeStockConfirmationException;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryApprovalStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import com.nextgenmanager.nextgenmanager.Inventory.model.ItemWarehouseStock;
import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryRequestRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.ItemWarehouseStockRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionService;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.Inventory.service.WorkOrderMaterialRequestServiceImpl;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialReorderRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Approving a material request for a work order that draws from a named store.
 *
 * <p>The only check was company-wide, so an order at Plant 2 was approved against stock sitting at
 * Plant 1 and Plant 2's balance went negative until somebody noticed and raised a transfer.
 */
@ExtendWith(MockitoExtension.class)
class MaterialRequestStoreGuardTest {

    @Mock private InventoryRequestRepository inventoryRequestRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private WorkOrderMaterialRepository workOrderMaterialRepository;
    @Mock private WorkOrderMaterialReorderRepository workOrderMaterialReorderRepository;
    @Mock private InventoryTransactionService inventoryTransactionService;
    @Mock private WarehouseService warehouseService;
    @Mock private ItemWarehouseStockRepository itemWarehouseStockRepository;

    @InjectMocks private WorkOrderMaterialRequestServiceImpl service;

    private static final int ITEM_ID = 31;
    private static final Long MR_ID = 700L;

    private InventoryItem bar;
    private Warehouse plantTwo;
    private WorkOrder workOrder;
    private InventoryRequest request;

    @BeforeEach
    void setUp() {
        bar = new InventoryItem();
        bar.setInventoryItemId(ITEM_ID);
        bar.setItemCode("RM-0001");
        ProductInventorySettings settings = new ProductInventorySettings();
        settings.setAvailableQuantity(500); // plenty in the company
        bar.setProductInventorySettings(settings);

        plantTwo = new Warehouse();
        plantTwo.setId(2L);
        plantTwo.setCode("P2");

        WorkCenter centre = new WorkCenter();
        centre.setWarehouse(plantTwo);

        workOrder = new WorkOrder();
        workOrder.setId(9);
        workOrder.setWorkOrderNumber("WO-9");
        workOrder.setWorkCenter(centre);
        // A status the sync leaves alone, so these tests stay about the guard.
        workOrder.setWorkOrderStatus(WorkOrderStatus.IN_PROGRESS);

        request = new InventoryRequest();
        request.setId(MR_ID);
        request.setInventoryItem(bar);
        request.setRequestSource(InventoryRequestSource.WORK_ORDER);
        request.setSourceId(9L);
        request.setRequestedQuantity(new BigDecimal("120"));
        request.setApprovalStatus(InventoryApprovalStatus.PENDING);

        lenient().when(inventoryRequestRepository.findById(MR_ID)).thenReturn(Optional.of(request));
        lenient().when(inventoryRequestRepository.saveAndFlush(any(InventoryRequest.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(workOrderRepository.findById(9)).thenReturn(Optional.of(workOrder));
        lenient().when(warehouseService.resolveByCodeOrDefault("P2")).thenReturn(plantTwo);
    }

    private void inPlantTwo(String onHand) {
        ItemWarehouseStock row = new ItemWarehouseStock();
        row.setInventoryItem(bar);
        row.setWarehouse(plantTwo);
        row.setOnHand(new BigDecimal(onHand));
        lenient().when(itemWarehouseStockRepository.find(ITEM_ID, 2L)).thenReturn(Optional.of(row));
    }

    @Test
    void stockInAnotherStoreAsksForConfirmationAndNamesTheStore() {
        inPlantTwo("20");

        assertThatThrownBy(() -> service.approveMaterialRequest(MR_ID, "stores", false))
                .isInstanceOf(NegativeStockConfirmationException.class)
                .hasMessageContaining("Only 20 of RM-0001 is in P2")
                .hasMessageContaining("Transfer the rest in first");
        verify(inventoryTransactionService, never()).reserveStock(any());
    }

    @Test
    void anApproverWhoConfirmsGoesAhead() {
        inPlantTwo("20");

        service.approveMaterialRequest(MR_ID, "stores", true);

        verify(inventoryTransactionService).reserveStock(any());
        assertThat(request.getApprovalStatus()).isEqualTo(InventoryApprovalStatus.APPROVED);
    }

    @Test
    void enoughInTheStoreNeedsNoConfirmation() {
        inPlantTwo("120");

        service.approveMaterialRequest(MR_ID, "stores", false);

        verify(inventoryTransactionService).reserveStock(any());
    }

    @Test
    void batchAndSerialStockCannotBeForcedAcrossStores() {
        // A unit cannot be reserved in a store it is not in. Confirming does not help.
        bar.getProductInventorySettings().setBatchTracked(true);
        inPlantTwo("20");

        assertThatThrownBy(() -> service.approveMaterialRequest(MR_ID, "stores", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transfer the rest into P2 first");
        verify(inventoryTransactionService, never()).reserveStock(any());
    }

    @Test
    void anOrderOnTheDefaultStoreIsCheckedCompanyWideOnly() {
        // No work centre names a store: this is every single-site installation, and nothing
        // about approving its requests has changed.
        workOrder.setWorkCenter(null);

        service.approveMaterialRequest(MR_ID, "stores", false);

        verify(inventoryTransactionService).reserveStock(any());
        verify(itemWarehouseStockRepository, never()).find(any(Integer.class), any());
    }
}
