package com.nextgenmanager.nextgenmanager.sales.analytics.repository;

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
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The revenue half of the CRM dashboard: what was ordered, by whom, and of what.
 *
 * <h3>Why this lives in {@code sales} and not beside the enquiry analytics</h3>
 * Every figure here is rooted in {@code salesOrder}. Putting it in {@code marketing.enquiry}
 * alongside {@code EnquiryAnalyticsRepository} would have been convenient and would have created a
 * dependency cycle: {@code sales} already imports {@code marketing} (an order names the enquiry and
 * the quotation it came from), so a marketing class reading {@code salesOrder} would close the
 * loop. Reading in this direction is free — this repository can join enquiry without anything new
 * pointing back.
 *
 * <h3>Two definitions everything below depends on</h3>
 *
 * <p><strong>Which orders count.</strong> {@link #LIVE_ORDER} — not deleted, not cancelled. Drafts
 * are deliberately <em>included</em> and separately disclosed rather than filtered out: a company
 * that has never switched on the approval workflow has every order sitting at
 * {@code approvalStatus = 'DRAFT'}, and a dashboard that filtered on approval would show them a
 * confident zero.
 *
 * <p><strong>What an order is worth.</strong> {@link #ORDER_VALUE} — taxable value: after discount,
 * before GST, and <em>excluding freight</em>. Freight is a header charge recovered from the
 * customer, not something sold, so counting it as revenue inflates intake and cannot be attributed
 * to any product line.
 *
 * <h3>The trap in line-level revenue</h3>
 * {@link #LINE_VALUE} recomputes the line from {@code qty x pricePerUnit} and then applies the
 * order's header discount, rather than reading {@code salesOrderItem.totalAmountOfProduct}. That
 * column looks exactly like the one you want and is wrong: the order form never computes it — it
 * writes {@code i.totalAmountOfProduct ?? 0} — and the header totals are built from
 * {@code qty x pricePerUnit} directly. Summing the stored column would report most products at zero
 * revenue while the headline tile above them showed the real figure.
 *
 * <p>Discount lands at the header in this product, and the order form applies that one percentage
 * uniformly to every line, so allocating it back pro-rata is not an approximation — it reproduces
 * the arithmetic the form used. Product revenue therefore ties to the headline, which is the only
 * reason the two may appear on one screen.
 *
 * <h3>SQL conventions</h3>
 * Identifiers unquoted, output aliases quoted. Flyway created the schema with unquoted DDL, so
 * Postgres folded every column name to lower case regardless of what
 * {@code PhysicalNamingStrategyStandardImpl} implies about the entity field names.
 */
public interface SalesAnalyticsRepository extends Repository<SalesOrder, Long> {

    /** Orders that represent real trading. Cancelled and soft-deleted rows are not trading. */
    String LIVE_ORDER = " so.deletedDate IS NULL AND so.status <> 'CANCELLED' ";

    /** The window, applied to the order date. */
    String IN_WINDOW = " so.orderDate BETWEEN :from AND :to ";

    /**
     * Taxable value: post-discount, pre-GST, freight excluded.
     *
     * <p>The three-arm COALESCE falls back to {@code subTotal - discountAmount} for rows written
     * before the header recalculated its totals, and to zero for rows with neither. A single null
     * here would otherwise poison an entire SUM.
     */
    String ORDER_VALUE =
            " COALESCE(so.taxableValue, so.subTotal - COALESCE(so.discountAmount, 0), 0) ";

    /** See the class note: recomputed, never read from {@code totalAmountOfProduct}. */
    String LINE_VALUE =
            " (COALESCE(soi.qty, 0) * COALESCE(soi.pricePerUnit, 0)"
          + "  * (1 - COALESCE(so.discountPercentage, 0) / 100.0)) ";

    // ------------------------------------------------------------------ headline

    /**
     * One row, seven figures.
     *
     * <p>Wrapped in a subquery so the value expression is written once rather than repeated inside
     * every aggregate — Postgres will not let a select-list alias be reused in the same select
     * list, and six copies of the definition is six places for it to drift.
     */
    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) AS \"orderCount\", "
          + "       COALESCE(SUM(t.val), 0) AS \"orderValue\", "
          + "       COUNT(DISTINCT t.cid) AS \"customerCount\", "
          + "       COUNT(*) FILTER (WHERE t.approved = 0) AS \"unapprovedCount\", "
          + "       COALESCE(SUM(t.val) FILTER (WHERE t.approved = 0), 0) AS \"unapprovedValue\", "
          + "       COUNT(*) FILTER (WHERE t.eid IS NOT NULL) AS \"fromEnquiryCount\", "
          + "       COUNT(*) FILTER (WHERE t.qid IS NOT NULL) AS \"fromQuotationCount\" "
          + "  FROM (SELECT so.customer_id AS cid, "
          + "               so.enquiry_id AS eid, "
          + "               so.quotation_id AS qid, "
          + "               CASE WHEN COALESCE(so.approvalStatus, 'DRAFT') = 'APPROVED' "
          + "                    THEN 1 ELSE 0 END AS approved, "
          + "               " + ORDER_VALUE + " AS val "
          + "          FROM salesOrder so "
          + "         WHERE " + LIVE_ORDER + " AND " + IN_WINDOW + ") t")
    RevenueHeadlineRow headline(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ------------------------------------------------------------------ customer mix

    /**
     * New versus repeat, decided by whether the customer traded before the window opened.
     *
     * <p>The LEFT JOIN against {@code prior} is what makes both halves come from one pass, so the
     * two revenue figures always add to the headline above them. Computing them as two independent
     * queries is how a "new + repeat" pair ends up not summing to the total it sits beneath.
     */
    @Query(nativeQuery = true, value =
            "WITH win AS ( "
          + "  SELECT so.customer_id AS cid, " + ORDER_VALUE + " AS val "
          + "    FROM salesOrder so "
          + "   WHERE " + LIVE_ORDER + " AND " + IN_WINDOW + "), "
          + "prior AS ( "
          + "  SELECT DISTINCT so.customer_id AS cid "
          + "    FROM salesOrder so "
          + "   WHERE " + LIVE_ORDER + " AND so.orderDate < :from) "
          + "SELECT COUNT(DISTINCT w.cid) FILTER (WHERE p.cid IS NULL) AS \"newCustomers\", "
          + "       COUNT(DISTINCT w.cid) FILTER (WHERE p.cid IS NOT NULL) AS \"repeatCustomers\", "
          + "       COALESCE(SUM(w.val) FILTER (WHERE p.cid IS NULL), 0) AS \"newRevenue\", "
          + "       COALESCE(SUM(w.val) FILTER (WHERE p.cid IS NOT NULL), 0) AS \"repeatRevenue\", "
          + "       COUNT(*) FILTER (WHERE p.cid IS NULL) AS \"newOrders\", "
          + "       COUNT(*) FILTER (WHERE p.cid IS NOT NULL) AS \"repeatOrders\" "
          + "  FROM win w LEFT JOIN prior p ON p.cid = w.cid")
    CustomerMixRow customerMix(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Top customers by revenue in the window.
     *
     * <p>The correlated {@code firstOrderEver} subquery ignores the window on purpose — it is what
     * lets the UI mark a row "repeat" by the same rule {@link #customerMix} used, instead of a
     * second definition that would disagree with the band above it.
     */
    @Query(nativeQuery = true, value =
            "SELECT c.id AS \"customerId\", "
          + "       COALESCE(NULLIF(TRIM(c.companyName), ''), 'Unnamed #' || c.id) AS \"label\", "
          + "       COUNT(*) AS \"orderCount\", "
          + "       COALESCE(SUM(" + ORDER_VALUE + "), 0) AS \"revenue\", "
          + "       MAX(so.orderDate) AS \"lastOrderDate\", "
          + "       (SELECT MIN(p.orderDate) FROM salesOrder p "
          + "         WHERE p.customer_id = c.id AND p.deletedDate IS NULL "
          + "           AND p.status <> 'CANCELLED') AS \"firstOrderEver\" "
          + "  FROM salesOrder so "
          + "  JOIN contact c ON c.id = so.customer_id "
          + " WHERE " + LIVE_ORDER + " AND " + IN_WINDOW
          + " GROUP BY c.id, c.companyName "
          + " ORDER BY 4 DESC "
          + " LIMIT :limit")
    List<TopCustomerRow> topCustomers(@Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("limit") int limit);

    /**
     * Accounts that have gone quiet. Stock — {@code cutoff} is derived from today, not the window.
     *
     * <p>Ranked by lifetime value, not by length of silence: this is a call list, and the largest
     * lapsed account is the one worth the call even when a smaller one has been quiet for longer.
     */
    @Query(nativeQuery = true, value =
            "SELECT c.id AS \"customerId\", "
          + "       COALESCE(NULLIF(TRIM(c.companyName), ''), 'Unnamed #' || c.id) AS \"label\", "
          + "       MAX(so.orderDate) AS \"lastOrderDate\", "
          + "       (CURRENT_DATE - MAX(so.orderDate)) AS \"daysSinceLastOrder\", "
          + "       COALESCE(SUM(" + ORDER_VALUE + "), 0) AS \"lifetimeValue\", "
          + "       COUNT(*) AS \"lifetimeOrders\" "
          + "  FROM salesOrder so "
          + "  JOIN contact c ON c.id = so.customer_id "
          + " WHERE " + LIVE_ORDER + " AND c.deletedDate IS NULL "
          + " GROUP BY c.id, c.companyName "
          + "HAVING MAX(so.orderDate) < :cutoff "
          + " ORDER BY 5 DESC "
          + " LIMIT :limit")
    List<DormantCustomerRow> dormantCustomers(@Param("cutoff") LocalDate cutoff,
                                              @Param("limit") int limit);

    // ------------------------------------------------------------------ products

    /**
     * Top products by revenue, with the header discount allocated down to the line.
     *
     * <p>{@code customerCount} rides along because it is what separates a top seller from a
     * concentration risk, and computing it separately would mean a second pass over the same join.
     */
    @Query(nativeQuery = true, value =
            "SELECT i.inventoryItemId AS \"itemId\", "
          + "       i.itemCode AS \"itemCode\", "
          + "       i.name AS \"itemName\", "
          + "       COALESCE(NULLIF(TRIM(i.itemGroupCode), ''), 'Ungrouped') AS \"itemGroup\", "
          + "       COALESCE(SUM(COALESCE(soi.qty, 0)), 0) AS \"qty\", "
          + "       COALESCE(SUM(" + LINE_VALUE + "), 0) AS \"revenue\", "
          + "       COUNT(DISTINCT so.id) AS \"orderCount\", "
          + "       COUNT(DISTINCT so.customer_id) AS \"customerCount\" "
          + "  FROM salesOrderItem soi "
          + "  JOIN salesOrder so ON so.id = soi.sales_order_id "
          + "  JOIN inventoryItem i ON i.inventoryItemId = soi.inventory_item_id "
          + " WHERE " + LIVE_ORDER + " AND " + IN_WINDOW
          + " GROUP BY i.inventoryItemId, i.itemCode, i.name, i.itemGroupCode "
          + " ORDER BY 6 DESC "
          + " LIMIT :limit")
    List<TopProductRow> topProducts(@Param("from") LocalDate from,
                                    @Param("to") LocalDate to,
                                    @Param("limit") int limit);

    /**
     * The same revenue rolled up to product family, computed from the full line set rather than
     * from the truncated top-N above — see {@link GroupRevenueRow} for why that distinction
     * matters.
     */
    @Query(nativeQuery = true, value =
            "SELECT COALESCE(NULLIF(TRIM(i.itemGroupCode), ''), 'Ungrouped') AS \"groupCode\", "
          + "       COALESCE(SUM(" + LINE_VALUE + "), 0) AS \"revenue\", "
          + "       COUNT(*) AS \"lineCount\", "
          + "       COUNT(DISTINCT i.inventoryItemId) AS \"itemCount\" "
          + "  FROM salesOrderItem soi "
          + "  JOIN salesOrder so ON so.id = soi.sales_order_id "
          + "  JOIN inventoryItem i ON i.inventoryItemId = soi.inventory_item_id "
          + " WHERE " + LIVE_ORDER + " AND " + IN_WINDOW
          + " GROUP BY 1 "
          + " ORDER BY 2 DESC")
    List<GroupRevenueRow> revenueByProductGroup(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    /**
     * Total line value in the window.
     *
     * <p>Exists so the service can state the tie-out between the sum of the product bars and the
     * headline intake figure. Where the two differ the cause is orders carrying no lines, and the
     * UI would rather say so than let a reader assume the chart is the whole story.
     */
    @Query(nativeQuery = true, value =
            "SELECT COALESCE(SUM(" + LINE_VALUE + "), 0) "
          + "  FROM salesOrderItem soi "
          + "  JOIN salesOrder so ON so.id = soi.sales_order_id "
          + " WHERE " + LIVE_ORDER + " AND " + IN_WINDOW)
    BigDecimal totalLineValue(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ------------------------------------------------------------------ opportunities

    /**
     * The biggest open deals on the desk right now. Stock — no window.
     *
     * <p>Company name falls back through the contact record, then the free-typed name on the
     * enquiry, then a placeholder. A large share of this register was raised against companies
     * nobody turned into a contact, and an inner join would quietly remove exactly the un-managed
     * deals this list exists to surface.
     */
    @Query(nativeQuery = true, value =
            "SELECT e.id AS \"enquiryId\", "
          + "       e.enqNo AS \"enqNo\", "
          + "       COALESCE(NULLIF(TRIM(e.opportunityName), ''), e.enqNo) AS \"title\", "
          + "       COALESCE(NULLIF(TRIM(c.companyName), ''), "
          + "                NULLIF(TRIM(e.manualCompanyName), ''), 'Unnamed') AS \"customer\", "
          + "       e.status AS \"status\", "
          + "       COALESCE(e.expectedRevenue, 0) AS \"expectedRevenue\", "
          + "       e.probability AS \"probability\", "
          + "       (COALESCE(e.expectedRevenue, 0) * e.probability / 100.0) AS \"weightedValue\", "
          + "       e.targetCloseDate AS \"targetCloseDate\", "
          + "       e.nextFollowupDate AS \"nextFollowupDate\", "
          + "       COALESCE(u.username, 'Unassigned') AS \"owner\", "
          + "       (CURRENT_DATE - e.enqDate) AS \"ageDays\" "
          + "  FROM enquiry e "
          + "  LEFT JOIN contact c ON c.id = e.contact_id "
          + "  LEFT JOIN appuser u ON u.id = e.assigned_to_id "
          + " WHERE e.deletedDate IS NULL "
          + "   AND e.status NOT IN ('CONVERTED', 'LOST', 'CLOSED', 'JUNK') "
          + " ORDER BY COALESCE(e.expectedRevenue, 0) DESC "
          + " LIMIT :limit")
    List<TopOpportunityRow> topOpenOpportunities(@Param("limit") int limit);

    /**
     * The enquiries that actually produced revenue in the window, biggest first.
     *
     * <p>Reached through {@code salesOrder.enquiry_id} rather than through the quotation chain,
     * because most orders in this register arrive against enquiries nobody formally quoted; ranked
     * through the quotation, the list would be a handful of rows attributing almost none of the
     * real revenue.
     *
     * <p>{@code expectedRevenue} is carried alongside {@code bookedValue} so the two can be read
     * against each other — the cheapest available check on whether the register's forecast figures
     * are estimates or guesses.
     */
    @Query(nativeQuery = true, value =
            "SELECT e.id AS \"enquiryId\", "
          + "       e.enqNo AS \"enqNo\", "
          + "       COALESCE(NULLIF(TRIM(e.opportunityName), ''), e.enqNo) AS \"title\", "
          + "       COALESCE(NULLIF(TRIM(c.companyName), ''), "
          + "                NULLIF(TRIM(e.manualCompanyName), ''), 'Unnamed') AS \"customer\", "
          + "       COALESCE(NULLIF(TRIM(e.enquirySource), ''), 'Unspecified') AS \"source\", "
          + "       COALESCE(SUM(" + ORDER_VALUE + "), 0) AS \"bookedValue\", "
          + "       COUNT(*) AS \"orderCount\", "
          + "       COALESCE(MAX(e.expectedRevenue), 0) AS \"expectedRevenue\" "
          + "  FROM salesOrder so "
          + "  JOIN enquiry e ON e.id = so.enquiry_id AND e.deletedDate IS NULL "
          + "  LEFT JOIN contact c ON c.id = e.contact_id "
          + " WHERE " + LIVE_ORDER + " AND " + IN_WINDOW
          + " GROUP BY e.id, e.enqNo, e.opportunityName, e.manualCompanyName, "
          + "          e.enquirySource, c.companyName "
          + " ORDER BY 6 DESC "
          + " LIMIT :limit")
    List<ConvertedEnquiryRow> topConvertedEnquiries(@Param("from") LocalDate from,
                                                    @Param("to") LocalDate to,
                                                    @Param("limit") int limit);

    // ------------------------------------------------------------------ receivables

    /**
     * Invoiced, collected and outstanding as of today. Stock — see {@link ReceivablesRow}.
     *
     * <p>Cancelled invoices are excluded; drafts are not, because an unsent draft invoice against a
     * delivered order is exactly the leak this tile should surface rather than hide.
     */
    @Query(nativeQuery = true, value =
            "SELECT COUNT(*) AS \"openInvoiceCount\", "
          + "       COALESCE(SUM(ti.totalPayableAmount), 0) AS \"invoicedTotal\", "
          + "       COALESCE(SUM(ti.paidAmount), 0) AS \"collected\", "
          + "       COALESCE(SUM(ti.totalPayableAmount - COALESCE(ti.paidAmount, 0)), 0) "
          + "           AS \"outstanding\", "
          + "       COALESCE(SUM(ti.totalPayableAmount - COALESCE(ti.paidAmount, 0)) "
          + "                FILTER (WHERE ti.dueDate IS NOT NULL AND ti.dueDate < CURRENT_DATE), 0) "
          + "           AS \"overdue\", "
          + "       COUNT(*) FILTER (WHERE ti.dueDate IS NOT NULL "
          + "                          AND ti.dueDate < CURRENT_DATE) AS \"overdueInvoiceCount\" "
          + "  FROM taxInvoice ti "
          + " WHERE ti.deletedDate IS NULL AND ti.status <> 'CANCELLED' "
          + "   AND ti.totalPayableAmount - COALESCE(ti.paidAmount, 0) > 0")
    ReceivablesRow receivables();

    // ------------------------------------------------------------------ trend

    /**
     * Dense intake trend. Same generated-series shape as the pipeline trend, and for the same
     * reason: grouping the rows themselves omits a month in which nothing was sold, and a line
     * chart draws a dead quarter as a gentle slope across the gap.
     *
     * <p>Invoiced value is dated by invoice date, not order date, so the two series answer
     * different questions on one axis — what we sold, and what we billed.
     */
    @Query(nativeQuery = true, value =
            "SELECT TO_CHAR(b.d, :fmt) AS \"bucket\", "
          + "  (SELECT COUNT(*) FROM salesOrder so "
          + "    WHERE " + LIVE_ORDER
          + "      AND so.orderDate >= CAST(b.d AS DATE) "
          + "      AND so.orderDate < CAST(b.d + CAST(:step AS INTERVAL) AS DATE)) AS \"orders\", "
          + "  (SELECT COALESCE(SUM(" + ORDER_VALUE + "), 0) FROM salesOrder so "
          + "    WHERE " + LIVE_ORDER
          + "      AND so.orderDate >= CAST(b.d AS DATE) "
          + "      AND so.orderDate < CAST(b.d + CAST(:step AS INTERVAL) AS DATE)) AS \"orderValue\", "
          + "  (SELECT COALESCE(SUM(ti.taxableValue), 0) FROM taxInvoice ti "
          + "    WHERE ti.deletedDate IS NULL AND ti.status <> 'CANCELLED' "
          + "      AND ti.invoiceDate >= CAST(b.d AS DATE) "
          + "      AND ti.invoiceDate < CAST(b.d + CAST(:step AS INTERVAL) AS DATE)) "
          + "           AS \"invoicedValue\" "
          + "FROM generate_series( "
          + "       DATE_TRUNC(:bucket, CAST(:from AS TIMESTAMP)), "
          + "       DATE_TRUNC(:bucket, CAST(:to AS TIMESTAMP)), "
          + "       CAST(:step AS INTERVAL)) AS b(d) "
          + "ORDER BY b.d")
    List<RevenueTrendRow> trend(@Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                @Param("bucket") String bucket,
                                @Param("step") String step,
                                @Param("fmt") String fmt);
}
