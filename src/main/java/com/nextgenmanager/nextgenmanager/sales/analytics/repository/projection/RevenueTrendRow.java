package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;

/** One bucket of the intake trend. Dense: an empty bucket is a zero row, never a missing one. */
public interface RevenueTrendRow {

    String getBucket();

    Long getOrders();

    BigDecimal getOrderValue();

    /** Value invoiced in the bucket, dated by invoice date rather than order date. */
    BigDecimal getInvoicedValue();
}
