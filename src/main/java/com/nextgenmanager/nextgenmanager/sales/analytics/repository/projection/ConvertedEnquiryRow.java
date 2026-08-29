package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;

/**
 * An enquiry that turned into money in the window, ranked by how much.
 *
 * <p>Reached through {@code salesOrder.enquiry_id} rather than through the quotation chain. Of
 * PEC's 2026 register only three orders in sixty-three name a quotation — the rest arrived by
 * phone, WhatsApp or mail against enquiries nobody formally quoted. Ranked through the quotation,
 * this list would be three rows long and would attribute none of the real revenue.
 */
public interface ConvertedEnquiryRow {

    Integer getEnquiryId();

    String getEnqNo();

    String getTitle();

    String getCustomer();

    String getSource();

    /** Order value booked against this enquiry inside the window. */
    BigDecimal getBookedValue();

    Long getOrderCount();

    /** What the enquiry was forecast to be worth, for comparison against what it became. */
    BigDecimal getExpectedRevenue();
}
