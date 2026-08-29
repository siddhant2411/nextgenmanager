package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection;

import java.math.BigDecimal;

/**
 * The cohort funnel: enquiries raised in the window, and how far each of them got.
 *
 * <h3>"Reached at least", not "is currently at"</h3>
 * Each figure counts enquiries that got <em>to or past</em> that rung, so the funnel decreases
 * monotonically and the gap between two rungs is a real drop-off. Bucketing by current status
 * instead produces a shape that looks like a funnel and is not one — an enquiry sitting in
 * NEGOTIATION would be missing from the QUOTED bar it certainly passed through.
 *
 * <h3>How far a closed enquiry got, without stage history</h3>
 * For an open enquiry the current status gives the rung directly. For one that was lost or closed,
 * the status says only that it ended — so the rank falls back to hard evidence instead of a guess:
 * a quotation exists, therefore it reached QUOTED; a conversation exists, therefore it reached
 * CONTACTED; otherwise NEW. That is honest but conservative, and it under-counts an enquiry that
 * was negotiated verbally and never quoted. Phase 2's stage history replaces the inference with the
 * record — until then {@link #getInferredRank()} reports how many rows leaned on it.
 */
public interface CrmFunnelRow {

    Long getReachedNew();
    Long getReachedContacted();
    Long getReachedQuoted();
    Long getReachedNegotiation();

    /** Reached an order. Counted from a live sales order, not from the CONVERTED status alone. */
    Long getReachedWon();

    BigDecimal getValueNew();
    BigDecimal getValueContacted();
    BigDecimal getValueQuoted();
    BigDecimal getValueNegotiation();
    BigDecimal getValueWon();

    /**
     * Cohort rows whose rung was inferred from evidence rather than read from status — i.e. closed
     * enquiries. The honesty figure for the whole chart: if it is large, the drop-off percentages
     * are a floor rather than a measurement.
     */
    Long getInferredRank();
}
