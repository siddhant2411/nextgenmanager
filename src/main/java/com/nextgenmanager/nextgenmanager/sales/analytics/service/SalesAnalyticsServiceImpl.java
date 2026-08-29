package com.nextgenmanager.nextgenmanager.sales.analytics.service;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmPeriod;
import com.nextgenmanager.nextgenmanager.sales.analytics.dto.SalesAnalyticsDTO;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.SalesAnalyticsRepository;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.ConvertedEnquiryRow;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.CustomerMixRow;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.DormantCustomerRow;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.GroupRevenueRow;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.ReceivablesRow;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.RevenueHeadlineRow;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.RevenueTrendRow;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.TopCustomerRow;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.TopOpportunityRow;
import com.nextgenmanager.nextgenmanager.sales.analytics.repository.projection.TopProductRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles the Revenue Desk from the repository's rows.
 *
 * <p>The service does the arithmetic the database should not: shares, deltas, averages and the
 * reconciliation between line value and header value. All of it goes through {@link #percent}, which
 * returns null rather than zero on an empty denominator — a tile reading 0% because nothing has
 * happened yet states something false, and a blank one states the truth.
 */
@Service
@RequiredArgsConstructor
public class SalesAnalyticsServiceImpl implements SalesAnalyticsService {

    /**
     * How many buckets the trend may draw, matching the pipeline trend.
     *
     * <p>The clamp exists because ALL_TIME resolves to sentinel bounds ending in the year 9999;
     * without it {@code generate_series} would be asked for several thousand years of empty months.
     */
    private static final int MAX_TREND_BUCKETS = 24;

    /** Below this many days the trend switches to weekly buckets, matching the pipeline trend. */
    private static final int WEEKLY_THRESHOLD_DAYS = 62;

    /** Rows in each ranking. Fifteen so the top-10 concentration figure has ten rows to read. */
    private static final int RANK_LIMIT = 15;

    /**
     * Silence after which an account is called dormant.
     *
     * <p>Six months rather than a quarter: capital-equipment buying cycles are long, and a
     * three-month threshold in this market flags healthy accounts as lapsed, which is how a
     * dormancy list gets ignored.
     */
    private static final int DORMANT_AFTER_DAYS = 180;

    /** The earliest date {@link CrmPeriod} uses as its open-ended lower bound. */
    private static final LocalDate SENTINEL_MIN = LocalDate.of(1900, 1, 1);

    private final SalesAnalyticsRepository repo;

    @Override
    @Transactional(readOnly = true)
    public SalesAnalyticsDTO getAnalytics(CrmPeriod period) {
        CrmPeriod prior = period.previous();

        RevenueHeadlineRow head = repo.headline(period.getFrom(), period.getTo());
        RevenueHeadlineRow prev = repo.headline(prior.getFrom(), prior.getTo());

        SalesAnalyticsDTO.Headline headline = headline(head);

        List<TopCustomerRow> customerRows =
                repo.topCustomers(period.getFrom(), period.getTo(), RANK_LIMIT);
        List<TopProductRow> productRows =
                repo.topProducts(period.getFrom(), period.getTo(), RANK_LIMIT);
        List<GroupRevenueRow> groupRows =
                repo.revenueByProductGroup(period.getFrom(), period.getTo());

        BigDecimal lineValue = nz(repo.totalLineValue(period.getFrom(), period.getTo()));

        return SalesAnalyticsDTO.builder()
                .period(period)
                .headline(headline)
                .previous(headline(prev))
                .customerMix(customerMix(repo.customerMix(period.getFrom(), period.getTo()), period))
                .trend(trend(period))
                .trendBucket(bucketFor(period))
                .topCustomers(customers(customerRows, headline.getOrderValue(), period))
                .concentration(concentration(customerRows, headline))
                .topProducts(products(productRows, lineValue))
                .byProductGroup(groups(groupRows, lineValue))
                .productCoverage(coverage(lineValue, headline.getOrderValue()))
                .topConvertedEnquiries(convertedEnquiries(
                        repo.topConvertedEnquiries(period.getFrom(), period.getTo(), RANK_LIMIT)))
                .topOpenOpportunities(opportunities(repo.topOpenOpportunities(RANK_LIMIT)))
                .dormantCustomers(dormant(repo.dormantCustomers(
                        LocalDate.now().minusDays(DORMANT_AFTER_DAYS), RANK_LIMIT)))
                .dormantAfterDays(DORMANT_AFTER_DAYS)
                .receivables(receivables(repo.receivables()))
                .build();
    }

    // ------------------------------------------------------------------ headline

    private SalesAnalyticsDTO.Headline headline(RevenueHeadlineRow r) {
        if (r == null) {
            return SalesAnalyticsDTO.Headline.builder()
                    .orderValue(BigDecimal.ZERO)
                    .unapprovedValue(BigDecimal.ZERO)
                    .build();
        }
        long orders = lng(r.getOrderCount());
        BigDecimal value = nz(r.getOrderValue());

        return SalesAnalyticsDTO.Headline.builder()
                .orderCount(orders)
                .orderValue(value)
                .customerCount(lng(r.getCustomerCount()))
                .averageOrderValue(divide(value, orders))
                .unapprovedCount(lng(r.getUnapprovedCount()))
                .unapprovedValue(nz(r.getUnapprovedValue()))
                .fromEnquiryCount(lng(r.getFromEnquiryCount()))
                .fromQuotationCount(lng(r.getFromQuotationCount()))
                .enquiryLinkedPercent(percent(lng(r.getFromEnquiryCount()), orders))
                .build();
    }

    // ------------------------------------------------------------------ customer mix

    /**
     * The new-versus-repeat split, plus the flag that says whether it means anything.
     *
     * <p>{@code meaningful} is false when the window has no "before" for a customer to have traded
     * in. On ALL_TIME every customer is new by construction, and rendering that as a finding would
     * tell a business with twenty years of loyal accounts that it has none.
     */
    private SalesAnalyticsDTO.CustomerMix customerMix(CustomerMixRow r, CrmPeriod period) {
        if (r == null) {
            return SalesAnalyticsDTO.CustomerMix.builder()
                    .newRevenue(BigDecimal.ZERO)
                    .repeatRevenue(BigDecimal.ZERO)
                    .meaningful(false)
                    .build();
        }
        BigDecimal newRev = nz(r.getNewRevenue());
        BigDecimal repeatRev = nz(r.getRepeatRevenue());

        return SalesAnalyticsDTO.CustomerMix.builder()
                .newCustomers(lng(r.getNewCustomers()))
                .repeatCustomers(lng(r.getRepeatCustomers()))
                .newRevenue(newRev)
                .repeatRevenue(repeatRev)
                .newOrders(lng(r.getNewOrders()))
                .repeatOrders(lng(r.getRepeatOrders()))
                .repeatRevenuePercent(share(repeatRev, newRev.add(repeatRev)))
                .repeatAvgOrderValue(divide(repeatRev, lng(r.getRepeatOrders())))
                .newAvgOrderValue(divide(newRev, lng(r.getNewOrders())))
                .meaningful(period.getFrom().isAfter(SENTINEL_MIN))
                .build();
    }

    // ------------------------------------------------------------------ customers

    private List<SalesAnalyticsDTO.CustomerRow> customers(List<TopCustomerRow> rows,
                                                          BigDecimal total,
                                                          CrmPeriod period) {
        return rows.stream()
                .map(r -> {
                    BigDecimal revenue = nz(r.getRevenue());
                    LocalDate first = r.getFirstOrderEver();
                    return SalesAnalyticsDTO.CustomerRow.builder()
                            .customerId(r.getCustomerId())
                            .label(r.getLabel())
                            .orderCount(lng(r.getOrderCount()))
                            .revenue(revenue)
                            .sharePercent(share(revenue, total))
                            .averageOrderValue(divide(revenue, lng(r.getOrderCount())))
                            .lastOrderDate(r.getLastOrderDate())
                            // Same rule the mix band used: traded before the window opened.
                            .repeatCustomer(first != null && first.isBefore(period.getFrom()))
                            .firstOrderEver(first)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Concentration from the ranked list.
     *
     * <p>Safe to read off a truncated ranking — unlike a group roll-up — because the rows are
     * ordered by revenue and only the head of the list is ever consulted. {@link #RANK_LIMIT} must
     * stay at or above ten for {@code top10Percent} to mean what it says.
     */
    private SalesAnalyticsDTO.Concentration concentration(List<TopCustomerRow> rows,
                                                          SalesAnalyticsDTO.Headline headline) {
        BigDecimal total = headline.getOrderValue();
        return SalesAnalyticsDTO.Concentration.builder()
                .topCustomerPercent(share(sumTop(rows, 1), total))
                .top5Percent(share(sumTop(rows, 5), total))
                .top10Percent(share(sumTop(rows, 10), total))
                .customerCount(headline.getCustomerCount())
                .build();
    }

    private BigDecimal sumTop(List<TopCustomerRow> rows, int n) {
        return rows.stream()
                .limit(n)
                .map(r -> nz(r.getRevenue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<SalesAnalyticsDTO.DormantRow> dormant(List<DormantCustomerRow> rows) {
        return rows.stream()
                .map(r -> SalesAnalyticsDTO.DormantRow.builder()
                        .customerId(r.getCustomerId())
                        .label(r.getLabel())
                        .lastOrderDate(r.getLastOrderDate())
                        .daysSinceLastOrder(r.getDaysSinceLastOrder())
                        .lifetimeValue(nz(r.getLifetimeValue()))
                        .lifetimeOrders(lng(r.getLifetimeOrders()))
                        .build())
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ products

    private List<SalesAnalyticsDTO.ProductRow> products(List<TopProductRow> rows, BigDecimal total) {
        return rows.stream()
                .map(r -> {
                    BigDecimal revenue = nz(r.getRevenue());
                    return SalesAnalyticsDTO.ProductRow.builder()
                            .itemId(r.getItemId())
                            .itemCode(r.getItemCode())
                            .itemName(r.getItemName())
                            .itemGroup(r.getItemGroup())
                            .qty(nz(r.getQty()))
                            .revenue(revenue)
                            .sharePercent(share(revenue, total))
                            .orderCount(lng(r.getOrderCount()))
                            .customerCount(lng(r.getCustomerCount()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<SalesAnalyticsDTO.GroupRow> groups(List<GroupRevenueRow> rows, BigDecimal total) {
        return rows.stream()
                .map(r -> {
                    BigDecimal revenue = nz(r.getRevenue());
                    return SalesAnalyticsDTO.GroupRow.builder()
                            .groupCode(r.getGroupCode())
                            .revenue(revenue)
                            .sharePercent(share(revenue, total))
                            .lineCount(lng(r.getLineCount()))
                            .itemCount(lng(r.getItemCount()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Whether the product bars account for the headline above them.
     *
     * <p>A rupee of tolerance, not exact equality: the line expression multiplies three numerics
     * and the header stores a rounded total, so the two can differ in the last paisa on a large
     * order without anything being wrong.
     */
    private SalesAnalyticsDTO.ProductCoverage coverage(BigDecimal lineValue, BigDecimal headerValue) {
        BigDecimal gap = lineValue.subtract(headerValue).abs();
        return SalesAnalyticsDTO.ProductCoverage.builder()
                .lineValue(lineValue)
                .headerValue(headerValue)
                .coveragePercent(share(lineValue, headerValue))
                .reconciled(gap.compareTo(BigDecimal.ONE) <= 0)
                .build();
    }

    // ------------------------------------------------------------------ enquiries

    private List<SalesAnalyticsDTO.ConvertedEnquiryRow> convertedEnquiries(List<ConvertedEnquiryRow> rows) {
        return rows.stream()
                .map(r -> {
                    BigDecimal booked = nz(r.getBookedValue());
                    BigDecimal expected = nz(r.getExpectedRevenue());
                    return SalesAnalyticsDTO.ConvertedEnquiryRow.builder()
                            .enquiryId(r.getEnquiryId())
                            .enqNo(r.getEnqNo())
                            .title(r.getTitle())
                            .customer(r.getCustomer())
                            .source(r.getSource())
                            .bookedValue(booked)
                            .orderCount(lng(r.getOrderCount()))
                            .expectedRevenue(expected)
                            .forecastAccuracyPercent(share(booked, expected))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<SalesAnalyticsDTO.OpportunityRow> opportunities(List<TopOpportunityRow> rows) {
        LocalDate today = LocalDate.now();
        return rows.stream()
                .map(r -> SalesAnalyticsDTO.OpportunityRow.builder()
                        .enquiryId(r.getEnquiryId())
                        .enqNo(r.getEnqNo())
                        .title(r.getTitle())
                        .customer(r.getCustomer())
                        .status(r.getStatus())
                        .expectedRevenue(nz(r.getExpectedRevenue()))
                        .probability(r.getProbability())
                        // Left null where probability was never set: an unforecast deal is not a
                        // deal forecast at zero, and averaging the two together would say it was.
                        .weightedValue(r.getWeightedValue())
                        .targetCloseDate(r.getTargetCloseDate())
                        .nextFollowupDate(r.getNextFollowupDate())
                        .owner(r.getOwner())
                        .ageDays(r.getAgeDays())
                        .followupOverdue(r.getNextFollowupDate() != null
                                && r.getNextFollowupDate().isBefore(today))
                        .build())
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ receivables

    private SalesAnalyticsDTO.Receivables receivables(ReceivablesRow r) {
        if (r == null) {
            return SalesAnalyticsDTO.Receivables.builder()
                    .invoicedTotal(BigDecimal.ZERO)
                    .collected(BigDecimal.ZERO)
                    .outstanding(BigDecimal.ZERO)
                    .overdue(BigDecimal.ZERO)
                    .build();
        }
        BigDecimal outstanding = nz(r.getOutstanding());
        BigDecimal overdue = nz(r.getOverdue());

        return SalesAnalyticsDTO.Receivables.builder()
                .openInvoiceCount(lng(r.getOpenInvoiceCount()))
                .invoicedTotal(nz(r.getInvoicedTotal()))
                .collected(nz(r.getCollected()))
                .outstanding(outstanding)
                .overdue(overdue)
                .overdueInvoiceCount(lng(r.getOverdueInvoiceCount()))
                .overduePercent(share(overdue, outstanding))
                .build();
    }

    // ------------------------------------------------------------------ trend

    private String bucketFor(CrmPeriod p) {
        long days = ChronoUnit.DAYS.between(p.getFrom(), p.getTo()) + 1;
        return days <= WEEKLY_THRESHOLD_DAYS ? "week" : "month";
    }

    /**
     * Dense buckets, clamped the same way the pipeline trend is.
     *
     * <p>The clamp is relative to the window's own end rather than to today, so a historical custom
     * range charts the range that was asked for instead of being dragged toward the present.
     */
    private List<SalesAnalyticsDTO.TrendPoint> trend(CrmPeriod p) {
        boolean weekly = "week".equals(bucketFor(p));

        LocalDate to = min(p.getTo(), LocalDate.now());
        LocalDate earliest = weekly
                ? to.minusWeeks(MAX_TREND_BUCKETS - 1L)
                : to.minusMonths(MAX_TREND_BUCKETS - 1L).withDayOfMonth(1);
        LocalDate from = max(p.getFrom(), earliest);

        if (from.isAfter(to)) {
            return List.of();
        }

        List<RevenueTrendRow> rows = weekly
                ? repo.trend(from, to, "week", "1 week", "IYYY-\"W\"IW")
                : repo.trend(from, to, "month", "1 month", "YYYY-MM");

        List<SalesAnalyticsDTO.TrendPoint> points = new ArrayList<>(rows.size());
        for (RevenueTrendRow r : rows) {
            points.add(SalesAnalyticsDTO.TrendPoint.builder()
                    .bucket(r.getBucket())
                    .orders(lng(r.getOrders()))
                    .orderValue(nz(r.getOrderValue()))
                    .invoicedValue(nz(r.getInvoicedValue()))
                    .build());
        }
        return points;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Null when the denominator is zero.
     *
     * <p>A tile showing 0% because nothing has happened yet says something false; showing nothing
     * says the truth. Every percentage on this screen goes through here or {@link #share}.
     */
    private static BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) return null;
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    /** The money-valued twin of {@link #percent}, with the same null-on-empty contract. */
    private static BigDecimal share(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) return null;
        return nz(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /** An average over zero events is not zero, it is unknown. */
    private static BigDecimal divide(BigDecimal total, long count) {
        if (count <= 0) return null;
        return nz(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static long lng(Long v) {
        return v != null ? v : 0L;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
