package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Headline numbers for the CRM dashboard.
 *
 * <h3>Flow here, stock in {@link #stock}</h3>
 * Everything at the top level of this object <em>happened inside {@link #period}</em>: leads raised,
 * enquiries closed, money booked. The state of the desk right now — open pipeline, overdue
 * follow-ups, work nobody has touched — lives in {@link #stock} and is not period-bounded. The two
 * are separated structurally because a comment was not enough: bounding "overdue follow-ups" to a
 * month yields a figure that means nothing and shrinks every time a month turns over, which reads
 * as improvement.
 *
 * <h3>Outcome, not status</h3>
 * {@link #won} and {@link #lost} come from the close reason's outcome, not from EnquiryStatus.
 * Status describes where an enquiry sits in the workflow; outcome describes what commercially
 * happened to it. Deriving a win rate from status was correct only by coincidence — the import
 * happened to set both from one source, and they diverge the first time a person closes an enquiry
 * by hand. The rule that reconciles the two (outcome, plus uncoded rows whose status is the only
 * evidence) is now a SQL predicate stated once in {@code EnquiryMetricsRepository}, not an addition
 * repeated at each call site.
 *
 * <p>The distinction that makes the rate mean anything: LOST is a competitive defeat and belongs in
 * the denominator. {@link #declinedByUs} and {@link #noEngagement} do not — we never lost those, we
 * either walked away or never got an answer. Lumping all three together is what made the hand-kept
 * register useless for measuring anything, so they are reported as separate figures here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnquirySummaryDTO {

    /** The window every flow figure below was measured over. Echoed back so the UI cannot mislabel it. */
    private CrmPeriod period;

    /**
     * The same shape, for the immediately preceding window of equal length — the comparison every
     * delta on the dashboard is drawn against. Null for ALL_TIME, and null inside itself: the prior
     * period does not carry its own prior period, so this nests exactly one level.
     */
    private EnquirySummaryDTO previous;

    // ---- flow: raised in the window --------------------------------------------------------

    /** Enquiries raised in the window. */
    private long leadsCreated;

    /** Expected value of everything raised in the window — what the pipeline gained. */
    private BigDecimal createdValue;

    // ---- flow: closed in the window --------------------------------------------------------

    /** Outcome WON, plus CONVERTED enquiries nobody has coded yet. */
    private long won;

    /** Outcome LOST, plus LOST-status enquiries nobody has coded yet. Quoted and beaten. */
    private long lost;

    /** Customer went silent. Never entered a competition, so never counts as a defeat. */
    private long noEngagement;

    /** We chose not to pursue: out of scope, quantity too low, no vendor price. */
    private long declinedByUs;

    /** Customer's project on hold. May come back; not closed against us. */
    private long deferred;

    /** Not a real enquiry. */
    private long invalid;

    /**
     * won / (won + lost), as a percentage to two decimals. Null when nothing has been decided in
     * the window — a rate of 0% and "no data" are different statements and the tile should say so.
     */
    private BigDecimal winRatePercent;

    /** Everything that closed in the window, whatever the outcome. */
    private long closedCount;

    /**
     * How many of those carry a close-reason code.
     *
     * <p>Everything above is only as honest as this number: if it is far below {@link #closedCount},
     * the split is mostly inferred from status and the dashboard must say as much rather than imply
     * precision.
     */
    private long codedCount;

    private BigDecimal wonRevenue;
    private BigDecimal lostRevenue;

    /** Pipeline value sitting behind enquiries that went silent. The cost of not following up. */
    private BigDecimal noEngagementRevenue;

    // ---- flow: money actually ordered ------------------------------------------------------

    /**
     * Taxable value booked as sales orders in the window.
     *
     * <p>{@link #wonRevenue} is the sum of expectedRevenue on won enquiries — a figure somebody
     * typed in when the enquiry was raised. This is what was ordered. The gap between the two is
     * worth looking at: it is the difference between what the pipeline promised and what it
     * delivered.
     */
    private BigDecimal bookedRevenue;

    /** Distinct enquiries that produced at least one live order in the window. */
    private long convertedToOrder;

    /** bookedRevenue / convertedToOrder. Null when nothing converted — not zero. */
    private BigDecimal avgDealSize;

    // ---- stock -----------------------------------------------------------------------------

    /** As-of-today figures. Never period-bounded. See {@link CrmStockDTO}. */
    private CrmStockDTO stock;
}
