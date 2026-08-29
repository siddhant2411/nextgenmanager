package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every grouped view the dashboard draws, in one response.
 *
 * <p>Deliberately one endpoint rather than six. The dashboard needs the funnel, the trend and four
 * breakdowns to render a single screen; six round trips would each re-derive the same period and
 * arrive at slightly different moments, so a user watching the page load would see tiles disagree
 * for a few hundred milliseconds. One call, one period, one snapshot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmAnalyticsDTO {

    /** The window everything below was measured over — except {@link #openByStage}. */
    private CrmPeriod period;

    /** Enquiries raised in the window, counted at every rung they reached. Flow. */
    private Funnel funnel;

    /** Dense buckets across the window: created, won, lost, booked. Flow. */
    private List<TrendPoint> trend;

    /** Which bucketing the trend used — "month" or "week". The UI labels the axis from this. */
    private String trendBucket;

    private List<Breakdown> bySource;
    private List<Breakdown> byOwner;
    private List<Breakdown> byOutcome;
    private List<Breakdown> byChannel;
    private List<Breakdown> byGeography;

    /**
     * Open enquiries by current stage. <strong>Stock, not flow</strong> — the one member of this
     * object the period does not touch.
     *
     * <p>It answers "what is on the desk right now", which is a different question from the funnel
     * above it. The UI must render the two in different bands; putting them side by side implies
     * they are the same measurement taken twice.
     */
    private List<Breakdown> openByStage;

    // ------------------------------------------------------------------ nested shapes

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Breakdown {
        private String key;
        private String label;
        private long count;
        private BigDecimal value;
        private long wonCount;
        private BigDecimal wonValue;

        /**
         * wonCount / count as a percentage, or null when the cohort is empty.
         *
         * <p>Note this is won-out-of-<em>raised</em>, not won-out-of-<em>decided</em>: for a cohort
         * breakdown most of the denominator is still open, so it is a conversion rate rather than a
         * win rate, and it will read lower than the headline win-rate tile. That is correct and the
         * UI labels it "conversion" for exactly that reason.
         */
        private BigDecimal conversionPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String bucket;
        private long created;
        private long won;
        private long lost;
        private BigDecimal bookedValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Funnel {
        private List<Stage> stages;

        /**
         * Cohort rows whose rung was inferred from evidence (a quotation exists, a conversation
         * exists) rather than read from an open status — i.e. enquiries that have already closed.
         *
         * <p>The honesty figure for the whole chart. If it is large relative to the cohort, the
         * drop-off percentages are a conservative floor rather than a measurement, because a deal
         * negotiated verbally and lost leaves no trace of how far it got. Phase 2's stage history
         * is what replaces the inference with a record.
         */
        private long inferredRank;

        /** Size of the cohort, i.e. the first rung. Denominator for every drop-off. */
        private long cohortSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stage {
        private String key;
        private String label;

        /** Enquiries that reached this rung or passed it. Decreases monotonically down the funnel. */
        private long reached;

        private BigDecimal value;

        /**
         * Percentage lost between the previous rung and this one. Null on the first rung, which has
         * nothing before it to leak from.
         */
        private BigDecimal dropOffPercent;
    }
}
