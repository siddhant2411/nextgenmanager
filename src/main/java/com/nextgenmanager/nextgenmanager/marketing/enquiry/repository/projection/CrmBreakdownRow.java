package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection;

import java.math.BigDecimal;

/**
 * One row of any "group the register by X" breakdown — source, owner, outcome, geography, channel.
 *
 * <p>Every breakdown is a <em>cohort</em>: enquiries raised inside the window, and what became of
 * them. That is what makes {@link #getWonCount()} answer a real question — "of the leads this
 * source sent us last quarter, how many did we win?" — rather than dividing this period's wins by
 * this period's leads, which are two different sets of enquiries and whose ratio means nothing.
 */
public interface CrmBreakdownRow {

    /** Stable identity for the row — a status name, a user id, a trimmed source string. */
    String getKey();

    /** What to render. Falls back to the key where there is nothing friendlier. */
    String getLabel();

    Long getCount();

    /** Expected value of the cohort. */
    BigDecimal getValue();

    /** How many of the cohort were won, on the same coalescing rule the summary uses. */
    Long getWonCount();

    BigDecimal getWonValue();
}
