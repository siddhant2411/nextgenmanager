package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.EnquiryFlowMetrics;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.projection.EnquiryStockMetrics;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/**
 * The dashboard's two queries.
 *
 * <h3>Why this exists next to {@link EnquiryRepository}</h3>
 * The summary used to fire roughly fifteen separate COUNT/SUM statements, one per tile. Adding a
 * period parameter to each would have made it thirty, because every figure is also computed for the
 * prior window to render a delta. Conditional aggregation collapses each set into a single pass:
 * two statements per window, four for a full dashboard load.
 *
 * <h3>The coalescing rule lives here, once</h3>
 * "Won" is not a status. It is {@code outcome = 'WON'} <em>plus</em> uncoded enquiries whose status
 * is the only evidence available. That rule was previously expressed as a
 * {@code + countUncodedByStatus(...)} addition at the call site, and copying it into every new
 * grouped query is how two tiles on one screen end up disagreeing. It is now a SQL predicate stated
 * once, in {@link #WON}, {@link #LOST} and {@link #INVALID}, and every consumer inherits it.
 *
 * <h3>Identifier casing</h3>
 * Identifiers are written <strong>unquoted</strong>. {@code PhysicalNamingStrategyStandardImpl}
 * emits the field name verbatim, but the schema was created by Flyway with unquoted DDL
 * ({@code enqdate date} in V1__baseline), so Postgres folded every column to lower case. Unquoted
 * references fold the same way and match; quoting {@code "enqDate"} would look more correct and
 * would fail at runtime. Output aliases <em>are</em> quoted — those are labels this query defines,
 * and the interface projection binds to them exactly as written.
 */
public interface EnquiryMetricsRepository extends Repository<Enquiry, Long> {

    // ------------------------------------------------------------------ shared predicates

    /** Raised inside the window. */
    String CREATED = "e.enqDate BETWEEN CAST(:from AS DATE) AND CAST(:to AS DATE)";

    /** Closed inside the window. Rows with no closedDate fall out — see closedWithoutDate. */
    String CLOSED = "e.closedDate BETWEEN CAST(:from AS DATE) AND CAST(:to AS DATE)";

    /** Coded with this outcome, or uncoded with the status that stands in for it. */
    String WON     = "(r.outcome = 'WON'     OR (e.close_reason_id IS NULL AND e.status = 'CONVERTED'))";
    String LOST    = "(r.outcome = 'LOST'    OR (e.close_reason_id IS NULL AND e.status = 'LOST'))";
    String INVALID = "(r.outcome = 'INVALID' OR (e.close_reason_id IS NULL AND e.status = 'JUNK'))";

    /**
     * Not in a terminal state.
     *
     * <p>JUNK is excluded, and that is a fix rather than a copy: the old
     * {@code countOverdueFollowups()} listed only CONVERTED, LOST and CLOSED, so a junk enquiry
     * with a stale nextFollowupDate inflated the overdue count permanently and could never be
     * cleared without editing the record.
     */
    String OPEN = "e.status NOT IN ('CONVERTED', 'LOST', 'CLOSED', 'JUNK')";

    /** An order that represents real business. Cancelled is not revenue; draft on an import is. */
    String LIVE_ORDER = "so.deletedDate IS NULL AND so.status <> 'CANCELLED'";

    /** Orders placed inside the window, joined back to a live enquiry. */
    String ORDERS_IN_WINDOW =
            "  FROM salesOrder so "
          + "  JOIN enquiry oe ON oe.id = so.enquiry_id AND oe.deletedDate IS NULL "
          + " WHERE " + LIVE_ORDER
          + "   AND so.orderDate BETWEEN CAST(:from AS DATE) AND CAST(:to AS DATE)";

    // ------------------------------------------------------------------ flow

    String FLOW_SQL =
            "SELECT "
          + "  COUNT(*) FILTER (WHERE " + CREATED + ") AS \"leadsCreated\", "
          + "  COALESCE(SUM(e.expectedRevenue) FILTER (WHERE " + CREATED + "), 0) AS \"createdValue\", "

          + "  COUNT(*) FILTER (WHERE " + CLOSED + " AND " + WON + ") AS \"won\", "
          + "  COUNT(*) FILTER (WHERE " + CLOSED + " AND " + LOST + ") AS \"lost\", "
          + "  COUNT(*) FILTER (WHERE " + CLOSED + " AND r.outcome = 'NO_ENGAGEMENT') AS \"noEngagement\", "
          + "  COUNT(*) FILTER (WHERE " + CLOSED + " AND r.outcome = 'DECLINED_BY_US') AS \"declinedByUs\", "
          + "  COUNT(*) FILTER (WHERE " + CLOSED + " AND r.outcome = 'DEFERRED') AS \"deferred\", "
          + "  COUNT(*) FILTER (WHERE " + CLOSED + " AND " + INVALID + ") AS \"invalid\", "

          + "  COALESCE(SUM(e.expectedRevenue) FILTER (WHERE " + CLOSED + " AND " + WON + "), 0) AS \"wonValue\", "
          + "  COALESCE(SUM(e.expectedRevenue) FILTER (WHERE " + CLOSED + " AND " + LOST + "), 0) AS \"lostValue\", "
          + "  COALESCE(SUM(e.expectedRevenue) FILTER (WHERE " + CLOSED
          + "           AND r.outcome = 'NO_ENGAGEMENT'), 0) AS \"noEngagementValue\", "

          + "  COUNT(*) FILTER (WHERE " + CLOSED + ") AS \"closedCount\", "
          + "  COUNT(*) FILTER (WHERE " + CLOSED + " AND e.close_reason_id IS NOT NULL) AS \"codedCount\", "

          + "  (SELECT COALESCE(SUM(so.taxableValue), 0) " + ORDERS_IN_WINDOW + ") AS \"bookedRevenue\", "
          + "  (SELECT COUNT(DISTINCT so.enquiry_id) " + ORDERS_IN_WINDOW + ") AS \"convertedToOrder\" "

          + "FROM enquiry e "
          + "LEFT JOIN enquiryCloseReason r ON r.id = e.close_reason_id "
          + "WHERE e.deletedDate IS NULL";

    @Query(value = FLOW_SQL, nativeQuery = true)
    EnquiryFlowMetrics flowMetrics(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ------------------------------------------------------------------ stock

    String NEVER_CONTACTED =
            "NOT EXISTS (SELECT 1 FROM enquiryConversationRecord c "
          + "             WHERE c.enquiry_conversation_id = e.id AND c.deletedDate IS NULL)";

    String STOCK_SQL =
            "SELECT "
          + "  COUNT(*) AS \"totalLeads\", "
          + "  COUNT(*) FILTER (WHERE " + OPEN + ") AS \"openCount\", "
          + "  COALESCE(SUM(e.expectedRevenue) FILTER (WHERE " + OPEN + "), 0) AS \"openPipeline\", "
          + "  COALESCE(SUM(e.expectedRevenue * e.probability / 100.0) "
          + "           FILTER (WHERE " + OPEN + " AND e.probability IS NOT NULL), 0) AS \"weightedPipeline\", "
          + "  COUNT(*) FILTER (WHERE " + OPEN + " AND e.probability IS NOT NULL) AS \"withProbability\", "

          + "  COUNT(*) FILTER (WHERE " + OPEN
          + "           AND e.nextFollowupDate < CURRENT_DATE) AS \"overdueFollowups\", "
          + "  COALESCE(SUM(e.expectedRevenue) FILTER (WHERE " + OPEN
          + "           AND e.nextFollowupDate < CURRENT_DATE), 0) AS \"overdueValue\", "

          + "  COUNT(*) FILTER (WHERE " + OPEN + " AND " + NEVER_CONTACTED + ") AS \"openNeverContacted\", "

          + "  COUNT(*) FILTER (WHERE NOT (" + OPEN + ") AND e.closedDate IS NULL) AS \"closedWithoutDate\" "

          + "FROM enquiry e "
          + "WHERE e.deletedDate IS NULL";

    /**
     * Takes no dates, and must not be given any. See {@link EnquiryStockMetrics}.
     *
     * <p>{@code CURRENT_DATE} rather than a bound parameter: "overdue" means overdue as the
     * database sees today, and threading a client-supplied date in would let a stale browser tab
     * quietly redefine it.
     */
    @Query(value = STOCK_SQL, nativeQuery = true)
    EnquiryStockMetrics stockMetrics();
}
