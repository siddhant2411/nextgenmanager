package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmAnalyticsDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmPeriod;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmStockDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquirySummaryDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryAnalyticsRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryMetricsRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.CrmBreakdownRow;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.CrmFunnelRow;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.CrmTrendRow;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.EnquiryFlowMetrics;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.EnquiryStockMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CrmAnalyticsServiceImpl implements CrmAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(CrmAnalyticsServiceImpl.class);

    /**
     * How many points the trend chart will draw before it starts dropping the oldest.
     *
     * <p>ALL_TIME spans 1900 to 9999. Handing those bounds to {@code generate_series} at one row per
     * month asks Postgres for ninety-seven thousand rows to draw a chart with twelve visible
     * points. The window is clamped relative to its own end rather than to today, so a historical
     * custom range still charts the range that was asked for.
     */
    private static final int MAX_TREND_BUCKETS = 24;

    /** Below this, months are too coarse to show anything and the chart switches to weeks. */
    private static final int WEEKLY_THRESHOLD_DAYS = 62;

    private final EnquiryMetricsRepository metrics;
    private final EnquiryAnalyticsRepository analytics;

    public CrmAnalyticsServiceImpl(EnquiryMetricsRepository metrics,
                                   EnquiryAnalyticsRepository analytics) {
        this.metrics = metrics;
        this.analytics = analytics;
    }

    // ------------------------------------------------------------------ summary

    @Override
    @Transactional(readOnly = true)
    public EnquirySummaryDTO getSummary(CrmPeriod period) {
        logger.debug("CRM summary for {} .. {}", period.getFrom(), period.getTo());

        EnquirySummaryDTO current = flowToDto(
                metrics.flowMetrics(period.getFrom(), period.getTo()), period);

        // Stock is computed once and hangs off the current period only. Attaching it to the prior
        // period too would imply there was a different "right now" last month.
        current.setStock(stockToDto(metrics.stockMetrics()));

        CrmPeriod prior = period.previous();
        if (prior != null) {
            current.setPrevious(flowToDto(
                    metrics.flowMetrics(prior.getFrom(), prior.getTo()), prior));
        }
        return current;
    }

    private EnquirySummaryDTO flowToDto(EnquiryFlowMetrics m, CrmPeriod period) {
        long won = lng(m.getWon());
        long lost = lng(m.getLost());
        long converted = lng(m.getConvertedToOrder());
        BigDecimal booked = nz(m.getBookedRevenue());

        return EnquirySummaryDTO.builder()
                .period(period)
                .leadsCreated(lng(m.getLeadsCreated()))
                .createdValue(nz(m.getCreatedValue()))
                .won(won)
                .lost(lost)
                .noEngagement(lng(m.getNoEngagement()))
                .declinedByUs(lng(m.getDeclinedByUs()))
                .deferred(lng(m.getDeferred()))
                .invalid(lng(m.getInvalid()))
                .winRatePercent(percent(won, won + lost))
                .closedCount(lng(m.getClosedCount()))
                .codedCount(lng(m.getCodedCount()))
                .wonRevenue(nz(m.getWonValue()))
                .lostRevenue(nz(m.getLostValue()))
                .noEngagementRevenue(nz(m.getNoEngagementValue()))
                .bookedRevenue(booked)
                .convertedToOrder(converted)
                .avgDealSize(converted == 0
                        ? null
                        : booked.divide(BigDecimal.valueOf(converted), 2, RoundingMode.HALF_UP))
                .build();
    }

    private CrmStockDTO stockToDto(EnquiryStockMetrics s) {
        long open = lng(s.getOpenCount());
        long withProb = lng(s.getWithProbability());

        return CrmStockDTO.builder()
                .totalLeads(lng(s.getTotalLeads()))
                .openCount(open)
                .openPipeline(nz(s.getOpenPipeline()))
                .weightedPipeline(nz(s.getWeightedPipeline()).setScale(2, RoundingMode.HALF_UP))
                .probabilityCoverage(open == 0
                        ? null
                        : (int) Math.round(withProb * 100.0 / open))
                .overdueFollowups(lng(s.getOverdueFollowups()))
                .overdueValue(nz(s.getOverdueValue()))
                .openNeverContacted(lng(s.getOpenNeverContacted()))
                .closedWithoutDate(lng(s.getClosedWithoutDate()))
                .build();
    }

    // ------------------------------------------------------------------ analytics

    @Override
    @Transactional(readOnly = true)
    public CrmAnalyticsDTO getAnalytics(CrmPeriod period) {
        LocalDate from = period.getFrom();
        LocalDate to = period.getTo();

        return CrmAnalyticsDTO.builder()
                .period(period)
                .funnel(funnel(analytics.funnel(from, to)))
                .trend(trend(period))
                .trendBucket(bucketFor(period))
                .bySource(breakdowns(analytics.bySource(from, to)))
                .byOwner(breakdowns(analytics.byOwner(from, to)))
                .byOutcome(breakdowns(analytics.byOutcome(from, to)))
                .byChannel(breakdowns(analytics.byChannel(from, to)))
                .byGeography(breakdowns(analytics.byGeography(from, to)))
                .openByStage(breakdowns(analytics.openByStage()))
                .build();
    }

    /**
     * Turns the five "reached at least" counts into stages with the drop-off between them.
     *
     * <p>The drop-off is computed against the rung above, not against the cohort, because the
     * question a funnel answers is "where are we losing them", and a percentage of the original
     * cohort hides a catastrophic leak late in the funnel behind a small absolute number.
     */
    private CrmAnalyticsDTO.Funnel funnel(CrmFunnelRow f) {
        if (f == null) {
            return CrmAnalyticsDTO.Funnel.builder()
                    .stages(List.of()).inferredRank(0).cohortSize(0).build();
        }

        String[] keys   = {"NEW", "CONTACTED", "QUOTED", "NEGOTIATION", "WON"};
        String[] labels = {"New", "Contacted", "Quoted", "Negotiation", "Won"};
        long[] reached = {
                lng(f.getReachedNew()), lng(f.getReachedContacted()), lng(f.getReachedQuoted()),
                lng(f.getReachedNegotiation()), lng(f.getReachedWon())
        };
        BigDecimal[] values = {
                nz(f.getValueNew()), nz(f.getValueContacted()), nz(f.getValueQuoted()),
                nz(f.getValueNegotiation()), nz(f.getValueWon())
        };

        List<CrmAnalyticsDTO.Stage> stages = new ArrayList<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            BigDecimal drop = null;
            if (i > 0 && reached[i - 1] > 0) {
                drop = percent(reached[i - 1] - reached[i], reached[i - 1]);
            }
            stages.add(CrmAnalyticsDTO.Stage.builder()
                    .key(keys[i]).label(labels[i])
                    .reached(reached[i]).value(values[i])
                    .dropOffPercent(drop)
                    .build());
        }

        return CrmAnalyticsDTO.Funnel.builder()
                .stages(stages)
                .inferredRank(lng(f.getInferredRank()))
                .cohortSize(reached[0])
                .build();
    }

    private List<CrmAnalyticsDTO.Breakdown> breakdowns(List<CrmBreakdownRow> rows) {
        return rows.stream()
                .map(r -> {
                    long count = lng(r.getCount());
                    long wonCount = lng(r.getWonCount());
                    return CrmAnalyticsDTO.Breakdown.builder()
                            .key(r.getKey())
                            .label(r.getLabel() != null ? r.getLabel() : r.getKey())
                            .count(count)
                            .value(nz(r.getValue()))
                            .wonCount(wonCount)
                            .wonValue(nz(r.getWonValue()))
                            .conversionPercent(percent(wonCount, count))
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ trend bucketing

    private String bucketFor(CrmPeriod p) {
        long days = ChronoUnit.DAYS.between(p.getFrom(), p.getTo()) + 1;
        return days <= WEEKLY_THRESHOLD_DAYS ? "week" : "month";
    }

    private List<CrmAnalyticsDTO.TrendPoint> trend(CrmPeriod p) {
        boolean weekly = "week".equals(bucketFor(p));

        // Clamp relative to the window's own end. ALL_TIME ends in the year 9999, which would
        // otherwise generate several thousand years of empty buckets; a historical custom range
        // still charts the range that was asked for rather than being dragged toward today.
        LocalDate to = min(p.getTo(), LocalDate.now());
        LocalDate earliest = weekly
                ? to.minusWeeks(MAX_TREND_BUCKETS - 1L)
                : to.minusMonths(MAX_TREND_BUCKETS - 1L).withDayOfMonth(1);
        LocalDate from = max(p.getFrom(), earliest);

        if (from.isAfter(to)) {
            return List.of();
        }

        List<CrmTrendRow> rows = weekly
                ? analytics.trend(from, to, "week", "1 week", "IYYY-\"W\"IW")
                : analytics.trend(from, to, "month", "1 month", "YYYY-MM");

        return rows.stream()
                .map(r -> CrmAnalyticsDTO.TrendPoint.builder()
                        .bucket(r.getBucket())
                        .created(lng(r.getCreated()))
                        .won(lng(r.getWon()))
                        .lost(lng(r.getLost()))
                        .bookedValue(nz(r.getBookedValue()))
                        .build())
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Null when the denominator is zero.
     *
     * <p>A tile showing 0% because nothing has closed yet says something false; showing nothing
     * says the truth. Every percentage on the dashboard goes through here for that reason.
     */
    private static BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) return null;
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
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
