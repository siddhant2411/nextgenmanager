package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.sales.events.SalesOrderApprovedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Routes MAKE_TO_ORDER shortfalls after a Sales Order is approved and committed.
 * Runs AFTER_COMMIT in its own transaction: a routing failure logs and leaves the
 * need on the Planning Desk, but never rolls back the approved Sales Order.
 */
@Component
@RequiredArgsConstructor
public class ProcurementRoutingListener {

    private static final Logger log = LoggerFactory.getLogger(ProcurementRoutingListener.class);

    private final ProcurementRoutingService procurementRoutingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSalesOrderApproved(SalesOrderApprovedEvent event) {
        try {
            procurementRoutingService.routeForSalesOrder(event.getSalesOrderId());
        } catch (Exception e) {
            log.error("Procurement routing failed for SO {} — needs remain on the Planning Desk",
                    event.getSalesOrderId(), e);
        }
    }
}
