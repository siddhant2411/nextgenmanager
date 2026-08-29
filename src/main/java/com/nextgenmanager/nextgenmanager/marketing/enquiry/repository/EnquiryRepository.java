package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {

    // ------------------------------------------------------------------ list query
    //
    // The joins and the filter clause are constants shared by the page query, its count query and
    // the export. Those three drifting apart is exactly how a filter comes to mean two different
    // things depending on which one you asked.

    String ENQUIRY_JOINS = """
        FROM enquiry e
        LEFT JOIN contact c ON e.contact_id = c.id
        LEFT JOIN appuser u ON e.assigned_to_id = u.id
        LEFT JOIN enquiryCloseReason r ON e.close_reason_id = r.id
        """;

    /**
     * Every date predicate spells out all six comparators and ends in an ELSE.
     *
     * The bug this replaces: the CASE handled only '=', '&lt;' and '&gt;'. A null comparator --
     * what any caller passing a date and nothing else produces -- matched no WHEN, so the CASE
     * yielded NULL, the enclosing AND yielded NULL, and the row was dropped. Every date filter
     * returned zero rows. The ELSE makes an unmapped comparator behave as '=' at the database
     * rather than silently emptying the result; EnquiryFilter rejects unknown comparators with a
     * 400 before they ever reach here, so the ELSE is a backstop and not the contract.
     *
     * enqNo is matched exactly. It used to be an ILIKE substring, which made ?enqNo=1 return 143
     * of 329 rows -- an identifier filter that cannot address a single record is not a filter.
     * enqNoContains keeps the substring behaviour for type-ahead boxes that genuinely want it.
     */
    String ENQUIRY_FILTERS = """
        WHERE e.deletedDate IS NULL
        AND (CAST(:enqNo AS TEXT) IS NULL OR UPPER(e.enqNo) = UPPER(CAST(:enqNo AS TEXT)))
        AND (CAST(:enqNoContains AS TEXT) IS NULL OR e.enqNo ILIKE CONCAT('%', CAST(:enqNoContains AS TEXT), '%'))
        AND (CAST(:companyName AS TEXT) IS NULL OR
            COALESCE(c.companyName, e.manualCompanyName, '') ILIKE CONCAT('%', CAST(:companyName AS TEXT), '%'))
        AND (CAST(:status AS TEXT) IS NULL OR e.status = CAST(:status AS TEXT))
        AND (CAST(:priority AS TEXT) IS NULL OR e.priority = CAST(:priority AS TEXT))
        AND (CAST(:outcome AS TEXT) IS NULL OR r.outcome = CAST(:outcome AS TEXT))
        AND (CAST(:closeReasonCode AS TEXT) IS NULL OR UPPER(r.code) = UPPER(CAST(:closeReasonCode AS TEXT)))
        AND (CAST(:enquirySource AS TEXT) IS NULL OR
            COALESCE(e.enquirySource, '') ILIKE CONCAT('%', CAST(:enquirySource AS TEXT), '%'))
        AND (CAST(:assignedToId AS BIGINT) IS NULL OR e.assigned_to_id = CAST(:assignedToId AS BIGINT))
        AND (CAST(:daysForNextFollowup AS INTEGER) IS NULL OR e.daysForNextFollowup = CAST(:daysForNextFollowup AS INTEGER))
        AND (CAST(:enqDateFrom AS DATE) IS NULL OR e.enqDate >= CAST(:enqDateFrom AS DATE))
        AND (CAST(:enqDateTo AS DATE) IS NULL OR e.enqDate <= CAST(:enqDateTo AS DATE))
        AND (CAST(:lastContactedDate AS DATE) IS NULL OR
            CASE COALESCE(CAST(:dateComparisonTypeLastContacted AS TEXT), '=')
                WHEN '='  THEN e.lastContactedDate =  CAST(:lastContactedDate AS DATE)
                WHEN '!=' THEN e.lastContactedDate <> CAST(:lastContactedDate AS DATE)
                WHEN '<'  THEN e.lastContactedDate <  CAST(:lastContactedDate AS DATE)
                WHEN '<=' THEN e.lastContactedDate <= CAST(:lastContactedDate AS DATE)
                WHEN '>'  THEN e.lastContactedDate >  CAST(:lastContactedDate AS DATE)
                WHEN '>=' THEN e.lastContactedDate >= CAST(:lastContactedDate AS DATE)
                ELSE e.lastContactedDate = CAST(:lastContactedDate AS DATE)
            END)
        AND (CAST(:enqDate AS DATE) IS NULL OR
            CASE COALESCE(CAST(:dateComparisonTypeEnqDate AS TEXT), '=')
                WHEN '='  THEN e.enqDate =  CAST(:enqDate AS DATE)
                WHEN '!=' THEN e.enqDate <> CAST(:enqDate AS DATE)
                WHEN '<'  THEN e.enqDate <  CAST(:enqDate AS DATE)
                WHEN '<=' THEN e.enqDate <= CAST(:enqDate AS DATE)
                WHEN '>'  THEN e.enqDate >  CAST(:enqDate AS DATE)
                WHEN '>=' THEN e.enqDate >= CAST(:enqDate AS DATE)
                ELSE e.enqDate = CAST(:enqDate AS DATE)
            END)
        AND (CAST(:aiGenerated AS BOOLEAN) IS NULL OR e.aiGenerated = CAST(:aiGenerated AS BOOLEAN))
        AND (CAST(:aiRequiresReview AS BOOLEAN) IS NULL OR e.aiRequiresReview = CAST(:aiRequiresReview AS BOOLEAN))
        AND (CAST(:gmailThreadId AS TEXT) IS NULL OR e.gmailThreadId = CAST(:gmailThreadId AS TEXT))
        AND (CAST(:gmailMessageId AS TEXT) IS NULL OR e.gmailMessageId = CAST(:gmailMessageId AS TEXT))
        AND (CAST(:closedDate AS DATE) IS NULL OR
            CASE COALESCE(CAST(:dateComparisonTypeClosedDate AS TEXT), '=')
                WHEN '='  THEN e.closedDate =  CAST(:closedDate AS DATE)
                WHEN '!=' THEN e.closedDate <> CAST(:closedDate AS DATE)
                WHEN '<'  THEN e.closedDate <  CAST(:closedDate AS DATE)
                WHEN '<=' THEN e.closedDate <= CAST(:closedDate AS DATE)
                WHEN '>'  THEN e.closedDate >  CAST(:closedDate AS DATE)
                WHEN '>=' THEN e.closedDate >= CAST(:closedDate AS DATE)
                ELSE e.closedDate = CAST(:closedDate AS DATE)
            END)
        """;

    /**
     * Columns 17 onwards are the N+1 fix. Close reason, source, contact id, a product summary and
     * the conversation counts were all absent from the list row, so any real analysis of the
     * register had to re-fetch all 329 enquiries one at a time -- 338 calls, 329 of them waste.
     * The aggregates are correlated subqueries rather than joins so a multi-item enquiry stays
     * one row and the page count keeps meaning what it says.
     *
     * Index order is a contract: EnquiryServiceImpl reads these positionally. Append, never insert.
     */
    String ENQUIRY_COLUMNS = """
        SELECT
            e.id as id, e.enqNo as enqNo, e.enqDate as enqDate,
            COALESCE(c.companyName, e.manualCompanyName) as companyName,
            e.lastContactedDate as lastContactedDate, e.daysForNextFollowup as daysForNextFollowup,
            e.closedDate as closedDate, e.status as status, e.expectedRevenue as expectedRevenue,
            e.opportunityName as opportunityName, COALESCE(e.contactPersonPhone, c.phone) as phone,
            COALESCE(e.contactPersonEmail, c.email) as email,
            e.priority as priority, e.city as city, e.state as state, u.username as assignedToName,
            e.nextFollowupDate as nextFollowupDate,
            e.contact_id as contactId,
            e.assigned_to_id as assignedToId,
            e.enquirySource as enquirySource,
            r.code as closeReasonCode,
            r.outcome as closeOutcome,
            e.closeReason as closeReasonText,
            (SELECT string_agg(COALESCE(ii.itemCode, ep.productNameRequired), ', ' ORDER BY ep.id)
               FROM enquiredProducts ep
               LEFT JOIN inventoryItem ii ON ep.inventory_item_id = ii.inventoryItemId
              WHERE ep.enquiry_id = e.id) as productSummary,
            (SELECT COUNT(*) FROM enquiredProducts ep2 WHERE ep2.enquiry_id = e.id) as productCount,
            (SELECT COUNT(*) FROM enquiryConversationRecord cr
              WHERE cr.enquiry_conversation_id = e.id AND cr.deletedDate IS NULL) as conversationCount,
            (SELECT MAX(COALESCE(cr2.conversationDate, CAST(cr2.creationDate AS DATE)))
               FROM enquiryConversationRecord cr2
              WHERE cr2.enquiry_conversation_id = e.id AND cr2.deletedDate IS NULL) as lastConversationDate,
            (SELECT COUNT(*) FROM quotation q WHERE q.enquiry_id = e.id) as quotationCount,
            e.probability as probability,
            (SELECT COUNT(*) FROM salesOrder so
              WHERE so.enquiry_id = e.id AND so.deletedDate IS NULL
                AND so.status <> 'CANCELLED') as salesOrderCount,
            (SELECT COALESCE(SUM(so2.taxableValue), 0) FROM salesOrder so2
              WHERE so2.enquiry_id = e.id AND so2.deletedDate IS NULL
                AND so2.status <> 'CANCELLED') as bookedAmount,
            e.aiGenerated as aiGenerated,
            e.aiConfidence as aiConfidence,
            e.aiRequiresReview as aiRequiresReview,
            e.gmailThreadId as gmailThreadId
        """;

    @Query(value = "SELECT * FROM enquiry e WHERE e.id=:id AND e.deletedDate IS NULL", nativeQuery = true)
    public Enquiry getActiveEnquiryById(@Param("id") Long id);

    @Query(nativeQuery = true,
           value = ENQUIRY_COLUMNS + ENQUIRY_JOINS + ENQUIRY_FILTERS,
           countQuery = "SELECT COUNT(*) " + ENQUIRY_JOINS + ENQUIRY_FILTERS)
    Page<Object[]> getActiveEnquiries(
            Pageable pageable,
            @Param("enqNo") String enqNo,
            @Param("enqNoContains") String enqNoContains,
            @Param("companyName") String companyName,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("outcome") String outcome,
            @Param("closeReasonCode") String closeReasonCode,
            @Param("enquirySource") String enquirySource,
            @Param("assignedToId") Long assignedToId,
            @Param("daysForNextFollowup") Integer daysForNextFollowup,
            @Param("enqDateFrom") LocalDate enqDateFrom,
            @Param("enqDateTo") LocalDate enqDateTo,
            @Param("lastContactedDate") LocalDate lastContactedDate,
            @Param("enqDate") LocalDate enqDate,
            @Param("closedDate") LocalDate closedDate,
            @Param("dateComparisonTypeLastContacted") String dateComparisonTypeLastContacted,
            @Param("dateComparisonTypeEnqDate") String dateComparisonTypeEnqDate,
            @Param("dateComparisonTypeClosedDate") String dateComparisonTypeClosedDate,
            @Param("aiGenerated") Boolean aiGenerated,
            @Param("aiRequiresReview") Boolean aiRequiresReview,
            @Param("gmailThreadId") String gmailThreadId,
            @Param("gmailMessageId") String gmailMessageId
    );

    public Optional<Enquiry> findByEnqNo(String enqNo);

    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) > 0 FROM enquiry e
        LEFT JOIN contact c ON e.contact_id = c.id
        WHERE e.deletedDate IS NULL
        AND (CAST(:enqDate AS DATE) IS NULL OR e.enqDate = CAST(:enqDate AS DATE))
        AND (:opportunityName IS NULL OR LOWER(e.opportunityname) = LOWER(:opportunityName))
        AND LOWER(COALESCE(c.companyName, e.manualCompanyName, '')) = LOWER(:companyName)
    """)
    boolean existsByDeduplicationKey(
        @Param("opportunityName") String opportunityName,
        @Param("companyName") String companyName,
        @Param("enqDate") LocalDate enqDate
    );


    // Dashboard metrics used to live here as ~15 separate COUNT/SUM statements, one per tile.
    // They now live in EnquiryMetricsRepository as two conditional-aggregation queries, so a
    // period parameter costs one statement rather than fifteen, and the "won = outcome WON plus
    // uncoded CONVERTED" rule is stated once in SQL instead of being re-added at each call site.
}
