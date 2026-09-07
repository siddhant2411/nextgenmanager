package com.nextgenmanager.nextgenmanager.quality;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.ItemWarehouseStockRepository;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionServiceImpl;
import com.nextgenmanager.nextgenmanager.Inventory.service.WarehouseService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * produceStock and the quality status on the instances it creates.
 *
 * <p>Found while verifying phase H step 2 against Postgres: a rejected quantity on an untracked
 * item was added to {@code availableQuantity} exactly like good stock, because produceStock only
 * ever created an instance for batch- or serial-tracked items. The FAILED status this step exists
 * to record had nowhere to live, so a rejected raw material silently rejoined good stock — the
 * exact failure "rejected goods stop vanishing" was meant to close.
 */
@ExtendWith(MockitoExtension.class)
class ProduceStockQualityStatusTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryInstanceRepository inventoryInstanceRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private ItemWarehouseStockRepository itemWarehouseStockRepository;
    @Mock private InventoryLedgerRepository inventoryLedgerRepository;
    @Mock private DomainEventPublisher eventPublisher;

    @InjectMocks private InventoryTransactionServiceImpl service;

    private static final int ITEM_ID = 1752;

    private InventoryItem casting;
    private Warehouse main;

    @BeforeEach
    void setUp() {
        main = new Warehouse();
        main.setId(1L);
        main.setCode("MAIN");

        casting = new InventoryItem();
        casting.setInventoryItemId(ITEM_ID);
        casting.setItemCode("RMCF80001");
        ProductInventorySettings settings = new ProductInventorySettings();
        settings.setBatchTracked(false);
        settings.setSerialTracked(false);
        settings.setAvailableQuantity(0);
        casting.setProductInventorySettings(settings);

        lenient().when(inventoryItemRepository.findByActiveId(ITEM_ID)).thenReturn(casting);
        lenient().when(warehouseService.resolveByCodeOrDefault(any())).thenReturn(main);
        lenient().when(itemWarehouseStockRepository.find(ITEM_ID, 1L)).thenReturn(java.util.Optional.empty());
        lenient().when(inventoryLedgerRepository.save(any(InventoryLedger.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private InventoryTransactionDTO receipt(double qty, QualityStatus quality) {
        InventoryTransactionDTO dto = new InventoryTransactionDTO();
        dto.setInventoryItemId(ITEM_ID);
        dto.setQuantity(qty);
        dto.setTransactionType("GRN");
        dto.setReferenceType(quality == QualityStatus.FAILED ? "GRN_REJECTED" : "GRN");
        dto.setReferenceDocNo("GRN-TEST-0001");
        dto.setWarehouse("MAIN");
        dto.setCostPerUnit(300);
        dto.setCreatedBy("admin");
        dto.setQualityStatus(quality);
        return dto;
    }

    @Test
    void anOrdinaryReceiptOnAnUntrackedItemCreatesNoInstance() {
        // Unchanged behaviour: an item with no batch/serial tracking and no quality flag is
        // carried by the scalars alone, exactly as before this fix.
        service.produceStock(receipt(10, null));

        verify(inventoryInstanceRepository, never()).save(any(InventoryInstance.class));
        assertThat(casting.getProductInventorySettings().getAvailableQuantity()).isEqualTo(10.0);
    }

    @Test
    void aRejectionOnAnUntrackedItemCreatesAFailedInstance() {
        service.produceStock(receipt(3, QualityStatus.FAILED));

        ArgumentCaptor<InventoryInstance> saved = ArgumentCaptor.forClass(InventoryInstance.class);
        verify(inventoryInstanceRepository).save(saved.capture());
        assertThat(saved.getValue().getQualityStatus()).isEqualTo(QualityStatus.FAILED);
        assertThat(saved.getValue().getQuantity()).isEqualByComparingTo("3");
        assertThat(saved.getValue().getInventoryInstanceStatus()).isEqualTo(InventoryInstanceStatus.AVAILABLE);
    }

    @Test
    void aPassedReceiptStillNeedsNoInstanceOnAnUntrackedItem() {
        // Naming PASSED explicitly is the same as not naming anything — it is the default, not a
        // reason to start creating instances for every ordinary receipt.
        service.produceStock(receipt(5, QualityStatus.PASSED));

        verify(inventoryInstanceRepository, never()).save(any(InventoryInstance.class));
    }
}
