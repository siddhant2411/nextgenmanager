package com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One open enquiry, ranked by what it is worth.
 *
 * <p><strong>Stock, not flow.</strong> This is the desk as it stands today; the period selector
 * must not reach it. "The biggest deals we had open in March" is a question nobody asks, and
 * answering it would make the list shrink every time a window rolled over.
 */
public interface TopOpportunityRow {

    Integer getEnquiryId();

    String getEnqNo();

    String getTitle();

    String getCustomer();

    String getStatus();

    BigDecimal getExpectedRevenue();

    Integer getProbability();

    /** expectedRevenue x probability. Null probability is treated as unforecast, not as zero. */
    BigDecimal getWeightedValue();

    LocalDate getTargetCloseDate();

    LocalDate getNextFollowupDate();

    String getOwner();

    /** Days since the enquiry was raised. */
    Integer getAgeDays();
}
