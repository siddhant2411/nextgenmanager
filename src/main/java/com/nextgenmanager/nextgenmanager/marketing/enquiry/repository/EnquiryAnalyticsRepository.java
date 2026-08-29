package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.CrmBreakdownRow;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.CrmFunnelRow;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.CrmTrendRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * The grouped half of the dashboard: funnel, trend, and every "by X" breakdown.
 *
 * <p>All of it is served from one service call so the dashboard makes two HTTP requests rather than
 * eight. The win/loss rule is imported from {@link EnquiryMetricsRepository} rather than restated —
 * that constant is the single definition of what "won" means, and a breakdown that disagreed with
 * the headline tile above it would be worse than no breakdown.
 *
 * <p>Identifiers unquoted, output aliases quoted — see {@link EnquiryMetricsRepository} for why.
 */
public interface EnquiryAnalyticsRepository extends Repository<Enquiry, Long> {

    String WON = EnquiryMetricsRepository.WON;
    String LOST = EnquiryMetricsRepository.LOST;
    String CREATED = EnquiryMetricsRepository.CREATED;
    String CLOSED = EnquiryMetricsRepository.CLOSED;

    /** The five columns every breakdown returns, so one projection serves all of them. */
    String BREAKDOWN_MEASURES =
            "  COUNT(*) AS \"count\", "
          + "  COALESCE(SUM(e.expectedRevenue), 0) AS \"value\", "
          + "  COUNT(*) FILTER (WHERE " + WON + ") AS \"wonCount\", "
          + "  COALESCE(SUM(e.expectedRevenue) FILTER (WHERE " + WON + "), 0) AS \"wonValue\" ";

    String BREAKDOWN_FROM =
            "  FROM enquiry e "
          + "  LEFT JOIN enquiryCloseReason r ON r.id = e.close_reason_id "
          + " WHERE e.deletedDate IS NULL AND ";

    // ------------------------------------------------------------------ funnel

    /**
     * Enquiries raised in the window, counted at every rung they reached or passed.
     *
     * <p>The ordering of the CASE is deliberate. A live sales order wins over everything — an
     * enquiry somebody forgot to mark CONVERTED still reached an order. Only once the open statuses
     * are exhausted does it fall through to evidence-based inference for closed rows.
     */
    @Query(nativeQuery = true, value =
            "WITH cohort AS ( "
          + "  SELECT COALESCE(e.expectedRevenue, 0) AS val, "
          + "         CASE "
          + "           WHEN EXISTS (SELECT 1 FROM salesOrder so WHERE so.enquiry_id = e.id "
          + "                          AND so.deletedDate IS NULL AND so.status <> 'CANCELLED') THEN 5 "
          + "           WHEN e.status = 'CONVERTED'   THEN 5 "
          + "           WHEN e.status = 'NEGOTIATION' THEN 4 "
          + "           WHEN e.status = 'QUOTED'      THEN 3 "
          + "           WHEN e.status IN ('CONTACTED', 'QUALIFIED', 'FOLLOW_UP') THEN 2 "
          + "           WHEN e.status = 'NEW'         THEN 1 "
          + "           WHEN EXISTS (SELECT 1 FROM quotation q WHERE q.enquiry_id = e.id "
          + "                          AND q.deletedDate IS NULL) THEN 3 "
          + "           WHEN EXISTS (SELECT 1 FROM enquiryConversationRecord c "
          + "                         WHERE c.enquiry_conversation_id = e.id "
          + "                           AND c.deletedDate IS NULL) THEN 2 "
          + "           ELSE 1 "
          + "         END AS rnk, "
          + "         CASE WHEN e.status IN ('LOST', 'CLOSED', 'JUNK') THEN 1 ELSE 0 END AS inferred "
          + "    FROM enquiry e "
          + "   WHERE e.deletedDate IS NULL AND " + CREATED
          + ") "
          + "SELECT "
          + "  COUNT(*) FILTER (WHERE rnk >= 1) AS \"reachedNew\", "
          + "  COUNT(*) FILTER (WHERE rnk >= 2) AS \"reachedContacted\", "
          + "  COUNT(*) FILTER (WHERE rnk >= 3) AS \"reachedQuoted\", "
          + "  COUNT(*) FILTER (WHERE rnk >= 4) AS \"reachedNegotiation\", "
          + "  COUNT(*) FILTER (WHERE rnk >= 5) AS \"reachedWon\", "
          + "  COALESCE(SUM(val) FILTER (WHERE rnk >= 1), 0) AS \"valueNew\", "
          + "  COALESCE(SUM(val) FILTER (WHERE rnk >= 2), 0) AS \"valueContacted\", "
          + "  COALESCE(SUM(val) FILTER (WHERE rnk >= 3), 0) AS \"valueQuoted\", "
          + "  COALESCE(SUM(val) FILTER (WHERE rnk >= 4), 0) AS \"valueNegotiation\", "
          + "  COALESCE(SUM(val) FILTER (WHERE rnk >= 5), 0) AS \"valueWon\", "
          + "  COALESCE(SUM(inferred), 0) AS \"inferredRank\" "
          + "FROM cohort")
    CrmFunnelRow funnel(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ------------------------------------------------------------------ breakdowns

    /**
     * Source is free text today, so this trims and folds blanks into "Unspecified" but cannot fix
     * "IndiaMart" / "Indiamart" / "India Mart" splitting into three rows. Phase 6's source master
     * is what makes this reliable; until then the UI says so.
     */
    @Query(nativeQuery = true, value =
            "SELECT COALESCE(NULLIF(TRIM(e.enquirySource), ''), 'Unspecified') AS \"key\", "
          + "       COALESCE(NULLIF(TRIM(e.enquirySource), ''), 'Unspecified') AS \"label\", "
          + BREAKDOWN_MEASURES + BREAKDOWN_FROM + CREATED
          + " GROUP BY 1, 2 ORDER BY 3 DESC")
    List<CrmBreakdownRow> bySource(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Unassigned is a row, never a filtered-out gap. It is usually the largest and worst-performing
     * bucket in the register, and leaving it out of the leaderboard is how it stays that way.
     */
    @Query(nativeQuery = true, value =
            "SELECT COALESCE(CAST(u.id AS VARCHAR), 'unassigned') AS \"key\", "
          + "       COALESCE(u.username, 'Unassigned') AS \"label\", "
          + BREAKDOWN_MEASURES
          + "  FROM enquiry e "
          + "  LEFT JOIN enquiryCloseReason r ON r.id = e.close_reason_id "
          + "  LEFT JOIN appuser u ON u.id = e.assigned_to_id "
          + " WHERE e.deletedDate IS NULL AND " + CREATED
          + " GROUP BY 1, 2 ORDER BY 3 DESC")
    List<CrmBreakdownRow> byOwner(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(nativeQuery = true, value =
            "SELECT COALESCE(NULLIF(TRIM(e.state), ''), 'Unspecified') AS \"key\", "
          + "       COALESCE(NULLIF(TRIM(e.state), ''), 'Unspecified') AS \"label\", "
          + BREAKDOWN_MEASURES + BREAKDOWN_FROM + CREATED
          + " GROUP BY 1, 2 ORDER BY 3 DESC")
    List<CrmBreakdownRow> byGeography(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Bounded on closedDate, not enqDate — unlike every other breakdown here.
     *
     * <p>"Why did enquiries close this quarter?" is a question about closures, so the cohort is
     * what closed in the window. Grouping the raised-in-window cohort by outcome instead would
     * report mostly nulls, because a lead raised this month has usually not closed yet.
     */
    @Query(nativeQuery = true, value =
            "SELECT COALESCE(r.outcome, CASE e.status "
          + "                             WHEN 'CONVERTED' THEN 'WON' "
          + "                             WHEN 'LOST'      THEN 'LOST' "
          + "                             WHEN 'JUNK'      THEN 'INVALID' "
          + "                             ELSE 'UNCODED' END) AS \"key\", "
          + "       COALESCE(r.code, 'Uncoded') AS \"label\", "
          + BREAKDOWN_MEASURES + BREAKDOWN_FROM + CLOSED
          + " GROUP BY 1, 2 ORDER BY 3 DESC")
    List<CrmBreakdownRow> byOutcome(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Activity mix, aggregated straight off the conversation table.
     *
     * <p>Two traps handled here. The date is {@code COALESCE(conversationDate, creationDate)},
     * matching the fallback the entity documents — imported history has a creationDate of the
     * import run, not of the call. And this deliberately does not walk
     * {@code Enquiry.enquiryConversationRecords}, which is a CascadeType.ALL @OneToMany and would
     * N+1 the whole register to produce six numbers.
     */
    @Query(nativeQuery = true, value =
            "SELECT c.conversationType AS \"key\", "
          + "       c.conversationType AS \"label\", "
          + "       COUNT(*) AS \"count\", "
          + "       CAST(0 AS NUMERIC) AS \"value\", "
          + "       CAST(0 AS BIGINT) AS \"wonCount\", "
          + "       CAST(0 AS NUMERIC) AS \"wonValue\" "
          + "  FROM enquiryConversationRecord c "
          + "  JOIN enquiry e ON e.id = c.enquiry_conversation_id AND e.deletedDate IS NULL "
          + " WHERE c.deletedDate IS NULL "
          + "   AND COALESCE(c.conversationDate, CAST(c.creationDate AS DATE)) "
          + "       BETWEEN CAST(:from AS DATE) AND CAST(:to AS DATE) "
          + " GROUP BY 1, 2 ORDER BY 3 DESC")
    List<CrmBreakdownRow> byChannel(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Open enquiries by current stage — <strong>stock</strong>, the one query here that takes no
     * period.
     *
     * <p>It answers "what is on the desk right now", which the cohort funnel above does not: the
     * funnel follows a group of enquiries forward through time, this one photographs the pile.
     * Both are legitimate; presenting either as the other is not.
     */
    @Query(nativeQuery = true, value =
            "SELECT e.status AS \"key\", "
          + "       e.status AS \"label\", "
          + BREAKDOWN_MEASURES
          + "  FROM enquiry e "
          + "  LEFT JOIN enquiryCloseReason r ON r.id = e.close_reason_id "
          + " WHERE e.deletedDate IS NULL AND " + EnquiryMetricsRepository.OPEN
          + " GROUP BY 1, 2 ORDER BY 3 DESC")
    List<CrmBreakdownRow> openByStage();

    // ------------------------------------------------------------------ trend

    /**
     * Dense buckets from a generated date series.
     *
     * <p>The series is the point: grouping the rows themselves would omit a month in which nothing
     * happened, and a line chart joins across an omitted point with a straight segment — drawing a
     * dead quarter as a gentle slope.
     *
     * <p>{@code bucket} is the date_trunc field ("month"/"week"), {@code step} the matching
     * interval ("1 month"/"1 week"), {@code fmt} the to_char pattern. The service picks all three
     * together; they must agree or the labels will not match the buckets.
     */
    @Query(nativeQuery = true, value =
            "SELECT TO_CHAR(b.d, :fmt) AS \"bucket\", "
          + "  (SELECT COUNT(*) FROM enquiry e "
          + "    WHERE e.deletedDate IS NULL "
          + "      AND e.enqDate >= CAST(b.d AS DATE) "
          + "      AND e.enqDate <  CAST(b.d + CAST(:step AS INTERVAL) AS DATE)) AS \"created\", "
          + "  (SELECT COUNT(*) FROM enquiry e "
          + "     LEFT JOIN enquiryCloseReason r ON r.id = e.close_reason_id "
          + "    WHERE e.deletedDate IS NULL "
          + "      AND e.closedDate >= CAST(b.d AS DATE) "
          + "      AND e.closedDate <  CAST(b.d + CAST(:step AS INTERVAL) AS DATE) "
          + "      AND " + WON + ") AS \"won\", "
          + "  (SELECT COUNT(*) FROM enquiry e "
          + "     LEFT JOIN enquiryCloseReason r ON r.id = e.close_reason_id "
          + "    WHERE e.deletedDate IS NULL "
          + "      AND e.closedDate >= CAST(b.d AS DATE) "
          + "      AND e.closedDate <  CAST(b.d + CAST(:step AS INTERVAL) AS DATE) "
          + "      AND " + LOST + ") AS \"lost\", "
          + "  (SELECT COALESCE(SUM(so.taxableValue), 0) FROM salesOrder so "
          + "     JOIN enquiry oe ON oe.id = so.enquiry_id AND oe.deletedDate IS NULL "
          + "    WHERE so.deletedDate IS NULL AND so.status <> 'CANCELLED' "
          + "      AND so.orderDate >= CAST(b.d AS DATE) "
          + "      AND so.orderDate <  CAST(b.d + CAST(:step AS INTERVAL) AS DATE)) AS \"bookedValue\" "
          + "FROM generate_series( "
          + "       DATE_TRUNC(:bucket, CAST(:from AS TIMESTAMP)), "
          + "       DATE_TRUNC(:bucket, CAST(:to   AS TIMESTAMP)), "
          + "       CAST(:step AS INTERVAL)) AS b(d) "
          + "ORDER BY b.d")
    List<CrmTrendRow> trend(@Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("bucket") String bucket,
                            @Param("step") String step,
                            @Param("fmt") String fmt);
}
