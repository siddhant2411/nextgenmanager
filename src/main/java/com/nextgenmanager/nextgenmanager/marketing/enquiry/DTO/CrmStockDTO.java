package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The state of the desk right now. Nothing here is period-bounded, and nothing here carries a
 * delta.
 *
 * <p>It sits in its own object rather than flat alongside the flow figures precisely so that
 * cannot drift. When "overdue follow-ups" was a sibling of "won this month" on one flat DTO, there
 * was nothing but a comment stopping the next person from period-filtering it — at which point the
 * count shrinks at the start of every month and reads as improvement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmStockDTO {

    /** Every live enquiry ever raised — the size of the register, not of the pipeline. */
    private long totalLeads;

    /** Enquiries not in a terminal state. */
    private long openCount;

    /** Expected value of everything still open. */
    private BigDecimal openPipeline;

    /**
     * Σ (expectedRevenue × probability ÷ 100) over open enquiries.
     *
     * <p>{@code probability} has been stored since the first release and read by nothing. Waking it
     * up is most of the value of this field — but it is only meaningful next to
     * {@link #probabilityCoverage}, which is why the two are never rendered apart.
     */
    private BigDecimal weightedPipeline;

    /**
     * Percentage of open enquiries that carry a probability at all, 0–100.
     *
     * <p>Null when nothing is open. If this reads 34%, the weighted figure is two-thirds guesswork
     * and the tile must say so rather than present a forecast.
     */
    private Integer probabilityCoverage;

    /** Open enquiries whose next follow-up date has passed. */
    private long overdueFollowups;

    /** Pipeline value sitting behind them — the cost of not calling back. */
    private BigDecimal overdueValue;

    /** Open enquiries with no conversation logged at all. Nobody has chased them even once. */
    private long openNeverContacted;

    /**
     * Terminal enquiries with no closedDate.
     *
     * <p>Every outcome figure is bounded on closedDate, so these rows fall out of every period.
     * Reporting the count lets the dashboard admit it is under-counting rather than quietly do it.
     */
    private long closedWithoutDate;
}
