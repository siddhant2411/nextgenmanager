package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.service.workorder.WorkOrderService;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisition;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionSource;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.repository.PurchaseRequisitionRepository;
import com.nextgenmanager.nextgenmanager.purchase.requisition.service.PurchaseRequisitionService;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cancelling a sales order used to leave every work order and requisition it had raised standing.
 * Supply nobody has acted on now goes with the order; supply somebody has started is left and named.
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderSupplyCancellationTest {

    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private PurchaseRequisitionRepository purchaseRequisitionRepository;
    @Mock private ObjectProvider<WorkOrderService> workOrderServiceProvider;
    @Mock private ObjectProvider<PurchaseRequisitionService> purchaseRequisitionServiceProvider;
    @Mock private WorkOrderService workOrderService;
    @Mock private PurchaseRequisitionService purchaseRequisitionService;

    private SalesOrderSupplyCancellation cancellation;

    private static final Long SO_ID = 5L;
    private SalesOrder order;

    @BeforeEach
    void setUp() {
        cancellation = new SalesOrderSupplyCancellation(workOrderRepository,
                purchaseRequisitionRepository, workOrderServiceProvider, purchaseRequisitionServiceProvider);

        order = new SalesOrder();
        order.setId(SO_ID);
        order.setOrderNumber("SO-1");

        lenient().when(workOrderServiceProvider.getObject()).thenReturn(workOrderService);
        lenient().when(purchaseRequisitionServiceProvider.getObject()).thenReturn(purchaseRequisitionService);
        lenient().when(workOrderRepository.findLiveBySalesOrder(SO_ID)).thenReturn(List.of());
        lenient().when(purchaseRequisitionRepository
                        .findBySourceAndSourceRefIdAndDeletedDateIsNull(PurchaseRequisitionSource.SALES_ORDER, SO_ID))
                .thenReturn(List.of());
    }

    private WorkOrder workOrder(int id, String number, WorkOrderStatus status) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setWorkOrderNumber(number);
        wo.setWorkOrderStatus(status);
        return wo;
    }

    private PurchaseRequisition requisition(long id, String number, PurchaseRequisitionStatus status) {
        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setId(id);
        pr.setPrNumber(number);
        pr.setStatus(status);
        return pr;
    }

    @Test
    void anOrderThatRaisedNothingHasNothingToSay() {
        SalesOrderSupplyCancellation.Outcome outcome = cancellation.cancelUnstartedSupply(order);

        assertThat(outcome.isEmpty()).isTrue();
        assertThat(outcome.summary()).isNull();
    }

    @Test
    void aWorkOrderNobodyStartedIsCancelledWithTheOrder() {
        when(workOrderRepository.findLiveBySalesOrder(SO_ID)).thenReturn(List.of(
                workOrder(11, "WO-11", WorkOrderStatus.CREATED),
                workOrder(12, "WO-12", WorkOrderStatus.READY_FOR_PRODUCTION)));

        SalesOrderSupplyCancellation.Outcome outcome = cancellation.cancelUnstartedSupply(order);

        verify(workOrderService).cancelWorkOrder(11);
        verify(workOrderService).cancelWorkOrder(12);
        assertThat(outcome.cancelled()).containsExactly("WO-11", "WO-12");
        assertThat(outcome.leftRunning()).isEmpty();
    }

    @Test
    void aWorkOrderInProgressIsLeftAndNamed() {
        // Material is on the floor and labour is booked. What it makes can be sold elsewhere;
        // that is a decision for a person, not a side effect of voiding an order.
        when(workOrderRepository.findLiveBySalesOrder(SO_ID)).thenReturn(List.of(
                workOrder(13, "WO-13", WorkOrderStatus.IN_PROGRESS)));

        SalesOrderSupplyCancellation.Outcome outcome = cancellation.cancelUnstartedSupply(order);

        verify(workOrderService, never()).cancelWorkOrder(anyInt());
        assertThat(outcome.leftRunning()).containsExactly("WO-13 (IN_PROGRESS)");
        assertThat(outcome.summary()).contains("Still open, review: WO-13 (IN_PROGRESS)");
    }

    @Test
    void finishedWorkOrdersAreNotMentioned() {
        when(workOrderRepository.findLiveBySalesOrder(SO_ID)).thenReturn(List.of(
                workOrder(14, "WO-14", WorkOrderStatus.COMPLETED),
                workOrder(15, "WO-15", WorkOrderStatus.CANCELLED)));

        assertThat(cancellation.cancelUnstartedSupply(order).isEmpty()).isTrue();
    }

    @Test
    void anOpenRequisitionIsCancelledAndOneAlreadyOrderedIsLeft() {
        when(purchaseRequisitionRepository
                .findBySourceAndSourceRefIdAndDeletedDateIsNull(PurchaseRequisitionSource.SALES_ORDER, SO_ID))
                .thenReturn(List.of(
                        requisition(21L, "PR-21", PurchaseRequisitionStatus.DRAFT),
                        requisition(22L, "PR-22", PurchaseRequisitionStatus.CLOSED),
                        requisition(23L, "PR-23", PurchaseRequisitionStatus.CANCELLED)));

        SalesOrderSupplyCancellation.Outcome outcome = cancellation.cancelUnstartedSupply(order);

        verify(purchaseRequisitionService).cancel(eq(21L), any());
        verify(purchaseRequisitionService, never()).cancel(eq(22L), any());
        verify(purchaseRequisitionService, never()).cancel(eq(23L), any());
        assertThat(outcome.cancelled()).containsExactly("PR-21");
        assertThat(outcome.leftRunning()).containsExactly("PR-22 (already ordered)");
        assertThat(outcome.summary())
                .isEqualTo("Cancelled with the order: PR-21. Still open, review: PR-22 (already ordered).");
    }

    @Test
    void nothingIsLookedUpForAnotherOrder() {
        cancellation.cancelUnstartedSupply(order);

        verify(workOrderRepository).findLiveBySalesOrder(SO_ID);
        verify(workOrderRepository, never()).findLiveBySalesOrder(6L);
        verify(purchaseRequisitionService, never()).cancel(anyLong(), any());
    }
}
