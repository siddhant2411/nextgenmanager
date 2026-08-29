package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One customer's trading in the window, plus the two dates that place them in history.
 *
 * <p>{@link #getFirstOrderEver()} is deliberately unbounded by the window while everything else on
 * the row is bounded by it. It is what decides new versus repeat, and reading it from inside the
 * window would classify every customer as new — the same trap {@link CustomerMixRow} documents.
 */
public interface TopCustomerRow {

    Integer getCustomerId();

    String getLabel();

    Long getOrderCount();

    BigDecimal getRevenue();

    /** Most recent order inside the window. */
    LocalDate getLastOrderDate();

    /** First live order this customer ever placed, ignoring the window entirely. */
    LocalDate getFirstOrderEver();
}
