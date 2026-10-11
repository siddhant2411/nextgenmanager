package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.repository.WarehouseRepository;
import com.nextgenmanager.nextgenmanager.production.mapper.WorkCenterResponseMapper;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.WorkCenterRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.workcenter.WorkCenterServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A work order reserves and consumes in its work centre's store, asking the work centre each
 * time. Changing that store under an open order would have it consume from a store that never
 * reserved anything for it.
 */
@ExtendWith(MockitoExtension.class)
class WorkCenterStoreChangeTest {

    @Mock private WorkCenterRepository workCenterRepository;
    @Mock private WorkCenterResponseMapper workCenterResponseMapper;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private WorkOrderRepository workOrderRepository;

    @InjectMocks private WorkCenterServiceImpl service;

    private static final int WC_ID = 4;

    private Warehouse plantOne, plantTwo;
    private WorkCenter existing;

    private Warehouse warehouse(long id, String code) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode(code);
        return w;
    }

    private WorkCenter incoming(Warehouse store) {
        WorkCenter wc = new WorkCenter();
        wc.setCenterCode("WC-CNC");
        wc.setCenterName("CNC");
        wc.setWarehouse(store);
        return wc;
    }

    private WorkOrder open(String number) {
        WorkOrder wo = new WorkOrder();
        wo.setWorkOrderNumber(number);
        return wo;
    }

    @BeforeEach
    void setUp() {
        plantOne = warehouse(1L, "P1");
        plantTwo = warehouse(2L, "P2");

        existing = new WorkCenter();
        existing.setId(WC_ID);
        existing.setCenterCode("WC-CNC");
        existing.setWarehouse(plantOne);

        lenient().when(workCenterRepository.findById(WC_ID)).thenReturn(Optional.of(existing));
        lenient().when(workCenterRepository.save(any(WorkCenter.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(warehouseRepository.findLiveById(1L)).thenReturn(Optional.of(plantOne));
        lenient().when(warehouseRepository.findLiveById(2L)).thenReturn(Optional.of(plantTwo));
        lenient().when(workOrderRepository.findUsingWorkCenter(anyInt(), anyList())).thenReturn(List.of());
    }

    @Test
    void theStoreCannotChangeWhileAWorkOrderIsOpenOnTheCentre() {
        when(workOrderRepository.findUsingWorkCenter(anyInt(), anyList()))
                .thenReturn(List.of(open("WO-7"), open("WO-9")));

        assertThatThrownBy(() -> service.updateWorkCenter(WC_ID, incoming(plantTwo)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WC-CNC")
                .hasMessageContaining("2 work orders are open on it (WO-7, WO-9)");
        assertThat(existing.getWarehouse()).isSameAs(plantOne);
    }

    @Test
    void theStoreChangesWhenNothingIsOpen() {
        service.updateWorkCenter(WC_ID, incoming(plantTwo));

        assertThat(existing.getWarehouse()).isSameAs(plantTwo);
    }

    @Test
    void anEditThatLeavesTheStoreAloneIsNeverHeldUp() {
        // Renaming a centre, or changing its rate, has nothing to do with stock. The open work
        // orders are not even looked up.
        assertThatCode(() -> service.updateWorkCenter(WC_ID, incoming(plantOne)))
                .doesNotThrowAnyException();

        verify(workOrderRepository, never()).findUsingWorkCenter(anyInt(), anyList());
    }

    @Test
    void clearingTheStoreIsAChangeToo() {
        when(workOrderRepository.findUsingWorkCenter(anyInt(), anyList()))
                .thenReturn(List.of(open("WO-7")));

        assertThatThrownBy(() -> service.updateWorkCenter(WC_ID, incoming(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 work order is open on it (WO-7)");
    }
}
