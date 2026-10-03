package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.PickList;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus;
import com.nextgenmanager.nextgenmanager.Inventory.repository.PickListRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryRepository;
import com.nextgenmanager.nextgenmanager.marketing.quotation.repository.QuotationRepository;
import com.nextgenmanager.nextgenmanager.sales.exception.InvalidSalesOrderStateException;
import com.nextgenmanager.nextgenmanager.sales.mapper.SalesOrderMapper;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Approval commits stock to an order. Cancelling used to change the status and nothing else, so
 * the stock stayed committed to an order that no longer existed — found by cancelling a test order
 * and watching its four units stay reserved.
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderCancelTest {

    @Mock private SalesOrderRepository salesOrderRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private QuotationRepository quotationRepository;
    @Mock private EnquiryRepository enquiryRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryInstanceService inventoryInstanceService;
    @Mock private PickListRepository pickListRepository;
    @Mock private SalesOrderMapper salesOrderMapper;
    @Mock private SalesOrderNumberGenerator orderNumberGenerator;
    @Mock private SalesOrderTaxCalculator taxCalculator;
    @Mock private SalesPaymentRepository salesPaymentRepository;
    @Mock private DomainEventPublisher domainEventPublisher;

    @InjectMocks private SalesOrderServiceImpl service;

    private static final Long SO_ID = 5L;
    private SalesOrder order;
    private SalesOrderItem reservedLine, unreservedLine;

    private SalesOrderItem line(long id, String code, Long requestId) {
        InventoryItem item = new InventoryItem();
        item.setInventoryItemId((int) id);
        item.setItemCode(code);
        SalesOrderItem l = new SalesOrderItem();
        l.setId(id);
        l.setInventoryItem(item);
        l.setQty(new BigDecimal("4"));
        l.setItemRequestId(requestId);
        return l;
    }

    private PickList pick(String number, PickListStatus status) {
        PickList p = new PickList();
        p.setPickNumber(number);
        p.setStatus(status);
        return p;
    }

    @BeforeEach
    void setUp() {
        reservedLine = line(1, "VALVE-1", 900L);
        unreservedLine = line(2, "BOLT-1", null);

        order = new SalesOrder();
        order.setId(SO_ID);
        order.setOrderNumber("SO-1");
        order.setStatus(SalesOrderStatus.APPROVED);
        order.setItems(new ArrayList<>(List.of(reservedLine, unreservedLine)));

        lenient().when(salesOrderRepository.findById(SO_ID)).thenReturn(Optional.of(order));
        lenient().when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(pickListRepository.findLiveBySalesOrder(SO_ID)).thenReturn(List.of());
        lenient().when(inventoryInstanceService.releaseRequest(anyLong())).thenReturn(new BigDecimal("4"));
    }

    @Test
    void cancellingReleasesWhatApprovalReserved() {
        service.cancel(SO_ID, null);

        verify(inventoryInstanceService).releaseRequest(900L);
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.CANCELLED);
        // Cleared, so nothing later mistakes the line for one that still holds stock.
        assertThat(reservedLine.getItemRequestId()).isNull();
    }

    @Test
    void aLineThatReservedNothingReleasesNothing() {
        reservedLine.setItemRequestId(null);

        service.cancel(SO_ID, null);

        verify(inventoryInstanceService, never()).releaseRequest(anyLong());
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.CANCELLED);
    }

    /**
     * A live pick has units on a trolley. Releasing the reservation underneath it would put them
     * back into free stock while somebody is holding them.
     */
    @Test
    void anOrderWithAPickInHandCannotBeCancelled() {
        when(pickListRepository.findLiveBySalesOrder(SO_ID))
                .thenReturn(List.of(pick("PK/0007", PickListStatus.PICKED)));

        assertThatThrownBy(() -> service.cancel(SO_ID, null))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("pick PK/0007 (PICKED) is still open")
                .hasMessageContaining("Cancel the pick first");

        verify(inventoryInstanceService, never()).releaseRequest(anyLong());
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.APPROVED);
        assertThat(reservedLine.getItemRequestId()).isEqualTo(900L);
    }

    @Test
    void finishedPicksDoNotHoldTheOrder() {
        when(pickListRepository.findLiveBySalesOrder(SO_ID)).thenReturn(List.of(
                pick("PK/0001", PickListStatus.CANCELLED),
                pick("PK/0002", PickListStatus.DISPATCHED)));

        service.cancel(SO_ID, null);

        verify(inventoryInstanceService).releaseRequest(900L);
        assertThat(order.getStatus()).isEqualTo(SalesOrderStatus.CANCELLED);
    }

    @Test
    void anAlreadyCancelledOrderIsRefusedBeforeAnythingIsReleased() {
        order.setStatus(SalesOrderStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancel(SO_ID, null))
                .isInstanceOf(InvalidSalesOrderStateException.class)
                .hasMessageContaining("cannot be cancelled in status CANCELLED");

        verify(inventoryInstanceService, never()).releaseRequest(anyLong());
    }
}
