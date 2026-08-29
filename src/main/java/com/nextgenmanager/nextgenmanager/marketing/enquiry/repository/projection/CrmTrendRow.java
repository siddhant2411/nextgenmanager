package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection;

import java.math.BigDecimal;

/**
 * One bucket of the trend chart.
 *
 * <p>Buckets are generated from a date series rather than from the rows themselves, so a month in
 * which nothing happened comes back as a zero rather than as a missing point. A line chart that
 * silently skips empty buckets draws a straight segment across the gap and reports a quiet month as
 * a gradual trend.
 *
 * <p>{@code created} is bounded on enqDate, {@code won} and {@code lost} on closedDate, and
 * {@code bookedValue} on the sales order's own orderDate — three different columns, which is why
 * this cannot be a single GROUP BY.
 */
public interface CrmTrendRow {

    /** Bucket label, already formatted by the database: "2026-08" monthly, "2026-W33" weekly. */
    String getBucket();

    Long getCreated();

    Long getWon();

    Long getLost();

    /** Taxable value of orders placed in the bucket. */
    BigDecimal getBookedValue();
}
