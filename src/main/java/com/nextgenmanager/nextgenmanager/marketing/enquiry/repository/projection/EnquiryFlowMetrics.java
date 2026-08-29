package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection;

import java.math.BigDecimal;

/**
 * Everything that <em>happened</em> inside a reporting window, in one query.
 *
 * <p>A Spring Data interface projection rather than {@code Object[]}: the enquiry list already
 * carries a native projection addressed by positional index, and the fragility of that is
 * documented at its call site. Named getters cannot silently shift when a column is inserted.
 *
 * <p>Counts are boxed. A native {@code COUNT} never returns null, but a projection getter that
 * unboxes null throws a confusing NPE deep inside the proxy, and the cost of the wrapper is
 * nothing next to the cost of debugging that.
 */
public interface EnquiryFlowMetrics {

    /** Enquiries raised in the window — bounded on enqDate. */
    Long getLeadsCreated();

    /** Expected value of everything raised in the window. What the pipeline gained. */
    BigDecimal getCreatedValue();

    // ---- outcomes, all bounded on closedDate ----------------------------------------------
    //
    // Each figure is "closed in the window with this outcome coded" plus, where a status is the
    // only evidence available, "closed in the window, uncoded, with the matching status". The two
    // halves cannot overlap: one requires a close reason, the other requires its absence.

    Long getWon();
    Long getLost();
    Long getNoEngagement();
    Long getDeclinedByUs();
    Long getDeferred();
    Long getInvalid();

    BigDecimal getWonValue();
    BigDecimal getLostValue();
    BigDecimal getNoEngagementValue();

    /** Everything that closed in the window, whatever the outcome. Denominator for coding coverage. */
    Long getClosedCount();

    /** Of those, how many carry a close-reason code. The honesty check on every figure above. */
    Long getCodedCount();

    // ---- money actually ordered, bounded on the sales order's own date ---------------------

    /** Taxable value booked against enquiries, for orders placed in the window. */
    BigDecimal getBookedRevenue();

    /** Distinct enquiries that produced at least one live order in the window. */
    Long getConvertedToOrder();
}
