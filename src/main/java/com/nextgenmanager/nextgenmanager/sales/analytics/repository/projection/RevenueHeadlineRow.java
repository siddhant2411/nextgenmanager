package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;

/**
 * The one-row headline for a window: what was ordered, by how many customers, and how much of it
 * is not actually confirmed yet.
 *
 * <p>{@link #getUnapprovedValue()} is the honesty figure for this whole screen. Order intake is
 * the number a sales manager quotes upward, and a register that counts unapproved drafts as intake
 * flatters itself. Rather than silently filtering drafts out — which would under-report for a
 * company that does not use the approval workflow at all — both figures are reported and the UI
 * discloses the split.
 */
public interface RevenueHeadlineRow {

    Long getOrderCount();

    /** Taxable value, i.e. after discount and before GST. Freight is excluded — see the repository. */
    BigDecimal getOrderValue();

    /** Distinct customers who placed at least one order in the window. */
    Long getCustomerCount();

    /** Orders whose approval status is anything other than APPROVED. */
    Long getUnapprovedCount();

    BigDecimal getUnapprovedValue();

    /** Orders that name the enquiry they came from — the coverage figure for source attribution. */
    Long getFromEnquiryCount();

    /** Orders that name a quotation. Typically far smaller: most orders here arrive without one. */
    Long getFromQuotationCount();
}
