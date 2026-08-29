package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A customer who used to order and has stopped.
 *
 * <p><strong>Stock, not flow.</strong> Dormancy is measured from today back to the last order,
 * never from the window — a customer who went quiet in March is exactly as dormant when you are
 * looking at a June report, and a period-bounded version of this list would empty itself every
 * time the window moved.
 *
 * <p>Ranked by lifetime value rather than by how long they have been silent, because the list is a
 * call list. Ten small accounts dormant for three years matter less than one large account dormant
 * for seven months.
 */
public interface DormantCustomerRow {

    Integer getCustomerId();

    String getLabel();

    LocalDate getLastOrderDate();

    Integer getDaysSinceLastOrder();

    /** Every live order ever placed, at taxable value. */
    BigDecimal getLifetimeValue();

    Long getLifetimeOrders();
}
