package com.nextgenmanager.nextgenmanager.sales.analytics.dto;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmPeriod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the Revenue Desk draws, in one response.
 *
 * <p>One endpoint rather than eight, for the same reason {@code CrmAnalyticsDTO} is: the screen
 * needs the headline, the trend, three rankings and the stock blocks to render once, and eight
 * round trips would each resolve the period a few hundred milliseconds apart and briefly disagree
 * on screen while the page loaded.
 *
 * <h3>The organising distinction — flow versus stock</h3>
 * Every field below is one or the other, and the two are never mixed inside a block.
 *
 * <ul>
 *   <li><strong>Flow</strong> — happened inside the window: {@link #headline}, {@link #previous},
 *       {@link #customerMix}, {@link #trend}, {@link #topCustomers}, {@link #topProducts},
 *       {@link #byProductGroup}, {@link #topConvertedEnquiries}.</li>
 *   <li><strong>Stock</strong> — the state of the business today, which the period selector must
 *       not touch: {@link #topOpenOpportunities}, {@link #dormantCustomers},
 *       {@link #receivables}.</li>
 * </ul>
 *
 * <p>Bounding a stock figure to a window produces a number that means nothing and shrinks every
 * time the window rolls over, which reads on screen as improvement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesAnalyticsDTO {

    /** The window every flow figure was measured over. */
    private CrmPeriod period;

    private Headline headline;

    /** The same headline for the window immediately before, so each tile can carry a delta. */
    private Headline previous;

    private CustomerMix customerMix;

    /** Dense buckets across the window: orders, order value, invoiced value. */
    private List<TrendPoint> trend;

    /** Which bucketing the trend used — "month" or "week". The UI labels its axis from this. */
    private String trendBucket;

    private List<CustomerRow> topCustomers;

    /** How much of the window's revenue the largest handful of customers accounted for. */
    private Concentration concentration;

    private List<ProductRow> topProducts;

    private List<GroupRow> byProductGroup;

    /** The tie-out between the product bars and the headline. See {@link ProductCoverage}. */
    private ProductCoverage productCoverage;

    private List<ConvertedEnquiryRow> topConvertedEnquiries;

    // ------------------------------------------------------------------ stock

    private List<OpportunityRow> topOpenOpportunities;

    private List<DormantRow> dormantCustomers;

    /** The silence threshold {@link #dormantCustomers} was cut at, so the UI can name it. */
    private Integer dormantAfterDays;

    private Receivables receivables;

    // ================================================================== nested shapes

    /**
     * Order intake for a window.
     *
     * <p>{@link #unapprovedValue} is the honesty figure for the whole screen: drafts are counted in
     * {@link #orderValue} and disclosed here, rather than filtered out. Filtering would report a
     * confident zero to any company that has not switched the approval workflow on.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Headline {

        private long orderCount;

        /** Taxable value — after discount, before GST, freight excluded. */
        private BigDecimal orderValue;

        private long customerCount;

        /** orderValue / orderCount, or null when nothing was ordered. */
        private BigDecimal averageOrderValue;

        private long unapprovedCount;

        private BigDecimal unapprovedValue;

        /** Orders that name the enquiry they came from — coverage for source attribution. */
        private long fromEnquiryCount;

        private long fromQuotationCount;

        /**
         * fromEnquiryCount / orderCount as a percentage, or null on an empty window.
         *
         * <p>Read it before trusting {@link SalesAnalyticsDTO#topConvertedEnquiries}: that ranking
         * can only see orders that name an enquiry, so at 40% coverage it is describing 40% of the
         * revenue and the UI has to say so.
         */
        private BigDecimal enquiryLinkedPercent;
    }

    /**
     * New versus repeat business for the window.
     *
     * <p>A customer is repeat if they traded at any point <em>before</em> the window opened — not
     * if they ordered twice inside it. {@link #meaningful} is false when the window has no before
     * (an all-time period), where the split is arithmetically correct and completely useless.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerMix {

        private long newCustomers;
        private long repeatCustomers;
        private BigDecimal newRevenue;
        private BigDecimal repeatRevenue;
        private long newOrders;
        private long repeatOrders;

        /** repeatRevenue as a percentage of the two together, or null when both are zero. */
        private BigDecimal repeatRevenuePercent;

        /** Average order value from repeat customers, or null when there were none. */
        private BigDecimal repeatAvgOrderValue;

        private BigDecimal newAvgOrderValue;

        /**
         * False when the window reaches back far enough to have no "before" — every customer then
         * classifies as new and the UI must explain the zero rather than draw it as a finding.
         */
        private boolean meaningful;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String bucket;
        private long orders;
        private BigDecimal orderValue;
        private BigDecimal invoicedValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerRow {
        private Integer customerId;
        private String label;
        private long orderCount;
        private BigDecimal revenue;

        /** Share of the window's total intake, as a percentage. */
        private BigDecimal sharePercent;

        private BigDecimal averageOrderValue;
        private LocalDate lastOrderDate;

        /** True when this customer had already traded before the window opened. */
        private boolean repeatCustomer;

        /** First live order ever placed. Null only if the row somehow has no history at all. */
        private LocalDate firstOrderEver;
    }

    /**
     * Revenue concentration.
     *
     * <p>The number worth acting on in a manufacturing SME, and the one a top-customer table hints
     * at without stating. Two businesses with identical turnover are different companies when one
     * of them would lose 60% of it by losing a single phone call.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Concentration {

        /** Share of window revenue held by the single largest customer. Null on an empty window. */
        private BigDecimal topCustomerPercent;

        private BigDecimal top5Percent;

        private BigDecimal top10Percent;

        /** Distinct customers who traded in the window — the denominator behind the shares. */
        private long customerCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductRow {
        private Integer itemId;
        private String itemCode;
        private String itemName;
        private String itemGroup;

        /** Summed in the item's own UOM — comparable within a row, never across rows. */
        private BigDecimal qty;

        private BigDecimal revenue;

        /** Share of the window's total line value. */
        private BigDecimal sharePercent;

        private long orderCount;

        /**
         * Distinct customers who bought it. What separates a top seller from a single large order
         * wearing a top-seller badge.
         */
        private long customerCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupRow {
        private String groupCode;
        private BigDecimal revenue;
        private BigDecimal sharePercent;
        private long lineCount;
        private long itemCount;
    }

    /**
     * Whether the product rankings account for the whole window.
     *
     * <p>Line value and header intake are computed from the same arithmetic, so they should agree.
     * When they do not the cause is orders carrying no item lines — imported headers, or orders
     * saved before the lines were added — and their revenue appears in the headline while
     * belonging to no product bar. Stating the gap is cheaper than fielding the question about why
     * the bars do not add up.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductCoverage {

        /** Total value across all order lines in the window. */
        private BigDecimal lineValue;

        /** Total intake from the order headers, i.e. the headline figure. */
        private BigDecimal headerValue;

        /** lineValue / headerValue as a percentage, or null when nothing was ordered. */
        private BigDecimal coveragePercent;

        /** True when the two agree within a rupee and the UI can stay quiet. */
        private boolean reconciled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConvertedEnquiryRow {
        private Integer enquiryId;
        private String enqNo;
        private String title;
        private String customer;
        private String source;
        private BigDecimal bookedValue;
        private long orderCount;

        /** What the enquiry was forecast at, for reading against what it actually booked. */
        private BigDecimal expectedRevenue;

        /**
         * bookedValue over expectedRevenue as a percentage; null when nothing was forecast.
         *
         * <p>Consistently far from 100 means the register's expected-revenue column is decorative,
         * and every weighted-pipeline figure resting on it is decorative too.
         */
        private BigDecimal forecastAccuracyPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpportunityRow {
        private Integer enquiryId;
        private String enqNo;
        private String title;
        private String customer;
        private String status;
        private BigDecimal expectedRevenue;
        private Integer probability;

        /** expectedRevenue x probability. Null where probability was never set. */
        private BigDecimal weightedValue;

        private LocalDate targetCloseDate;
        private LocalDate nextFollowupDate;
        private String owner;
        private Integer ageDays;

        /** True when the follow-up date has passed. The one call-to-action on the row. */
        private boolean followupOverdue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DormantRow {
        private Integer customerId;
        private String label;
        private LocalDate lastOrderDate;
        private Integer daysSinceLastOrder;
        private BigDecimal lifetimeValue;
        private long lifetimeOrders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Receivables {

        private long openInvoiceCount;

        /** Gross, including GST — this is a cash figure, not a revenue figure. */
        private BigDecimal invoicedTotal;

        private BigDecimal collected;
        private BigDecimal outstanding;
        private BigDecimal overdue;
        private long overdueInvoiceCount;

        /** overdue as a percentage of outstanding, or null when nothing is outstanding. */
        private BigDecimal overduePercent;
    }
}
