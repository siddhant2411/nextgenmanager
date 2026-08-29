package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection;

import java.math.BigDecimal;

/**
 * The state of the desk <em>right now</em>, in one query.
 *
 * <p>Nothing here takes a period, and that is the point. These are as-of-today figures: how much is
 * open, how much is overdue, how much nobody has touched. Bounding "overdue follow-ups" to a month
 * yields a number that means nothing and shrinks at the start of every month, which reads as
 * improvement — so the repository method that fills this projection deliberately accepts no dates
 * and cannot be called with any.
 */
public interface EnquiryStockMetrics {

    /** Every live enquiry ever raised. The size of the register, not of the pipeline. */
    Long getTotalLeads();

    /** Enquiries not in a terminal state — the pipeline proper. */
    Long getOpenCount();

    /** Expected value of everything still open. */
    BigDecimal getOpenPipeline();

    /**
     * Σ (expectedRevenue × probability ÷ 100) over open enquiries that have a probability.
     *
     * <p>{@code probability} has been on the entity since the first release and was read by
     * nothing. Enquiries with no probability contribute zero here rather than being treated as
     * certain or as impossible — which is why {@link #getProbabilityCoverage()} has to travel
     * beside this figure for it to mean anything.
     */
    BigDecimal getWeightedPipeline();

    /** Open enquiries carrying a probability at all. Numerator for the coverage percentage. */
    Long getWithProbability();

    // The funnel deliberately does NOT live here. A funnel is a cohort — enquiries raised in a
    // window, and how far each of them got — which makes it a flow metric, bounded on enqDate.
    // Bucketing today's open enquiries by status instead produces a shape that looks like a funnel
    // and answers no question: it mixes a lead raised this morning with one that has been sitting
    // in NEGOTIATION for eight months. See EnquiryAnalyticsRepository.funnel(...).

    // ---- hygiene ---------------------------------------------------------------------------

    /** Open enquiries whose next follow-up date has passed. */
    Long getOverdueFollowups();

    /** Pipeline value sitting behind those overdue follow-ups. The cost of not calling back. */
    BigDecimal getOverdueValue();

    /** Open enquiries with no conversation record at all — nobody has chased them even once. */
    Long getOpenNeverContacted();

    /**
     * Enquiries in a terminal state with no closedDate.
     *
     * <p>A data-quality figure, and a load-bearing one: every outcome metric is bounded on
     * closedDate, so these rows fall out of every period. Reporting the count lets the dashboard
     * say so instead of quietly under-counting wins.
     */
    Long getClosedWithoutDate();
}
