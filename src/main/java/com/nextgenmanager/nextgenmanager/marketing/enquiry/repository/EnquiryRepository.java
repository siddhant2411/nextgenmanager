package com.nextgenmanager.nextgenmanager.marketing.enquiry.repository;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {



    @Query(value = "SELECT * FROM enquiry e WHERE e.id=:id AND e.deletedDate IS NULL", nativeQuery = true)
    public Enquiry getActiveEnquiryById(@Param("id") Long id);


    @Query(nativeQuery = true, value = """
    SELECT
        e.id as id, e.enqNo as enqNo, e.enqDate as enqDate,
        COALESCE(c.companyName, e.manualCompanyName) as companyName,
        e.lastContactedDate as lastContactedDate, e.daysForNextFollowup as daysForNextFollowup,
        e.closedDate as closedDate, e.status as status, e.expectedRevenue as expectedRevenue,
        e.opportunityname as opportunityName, COALESCE(e.contactPersonPhone, c.phone) as phone,
        COALESCE(e.contactPersonEmail, c.email) as email,
        e.priority as priority, e.city as city, e.state as state, u.username as assignedToName,
        e.nextFollowupDate as nextFollowupDate
    FROM enquiry e
    LEFT JOIN contact c ON e.contact_id = c.id
    LEFT JOIN appuser u ON e.assigned_to_id = u.id
    WHERE e.deletedDate IS NULL
    AND (CAST(:enqNo AS TEXT) IS NULL OR e.enqNo ILIKE CONCAT('%', CAST(:enqNo AS TEXT), '%'))
    AND (CAST(:companyName AS TEXT) IS NULL OR CAST(:companyName AS TEXT) = '' OR
        COALESCE(c.companyName, e.manualCompanyName, '') ILIKE CONCAT('%', CAST(:companyName AS TEXT), '%'))
    AND (CAST(:lastContactedDate AS DATE) IS NULL OR
        CASE CAST(:dateComparisonTypeLastContacted AS TEXT)
            WHEN '=' THEN e.lastContactedDate = CAST(:lastContactedDate AS DATE)
            WHEN '<' THEN e.lastContactedDate < CAST(:lastContactedDate AS DATE)
            WHEN '>' THEN e.lastContactedDate > CAST(:lastContactedDate AS DATE)
        END)
    AND (CAST(:daysForNextFollowup AS INTEGER) IS NULL OR e.daysForNextFollowup = CAST(:daysForNextFollowup AS INTEGER))
    AND (CAST(:enqDate AS DATE) IS NULL OR
        CASE CAST(:dateComparisonTypeEnqDate AS TEXT)
            WHEN '=' THEN e.enqDate = CAST(:enqDate AS DATE)
            WHEN '<' THEN e.enqDate < CAST(:enqDate AS DATE)
            WHEN '>' THEN e.enqDate > CAST(:enqDate AS DATE)
        END)
    AND (CAST(:closedDate AS DATE) IS NULL OR
        CASE CAST(:dateComparisonTypeClosedDate AS TEXT)
            WHEN '=' THEN e.closedDate = CAST(:closedDate AS DATE)
            WHEN '<' THEN e.closedDate < CAST(:closedDate AS DATE)
            WHEN '>' THEN e.closedDate > CAST(:closedDate AS DATE)
        END)
    """, countQuery = """
    SELECT COUNT(*)
    FROM enquiry e
    LEFT JOIN contact c ON e.contact_id = c.id
    WHERE e.deletedDate IS NULL
    AND (CAST(:enqNo AS TEXT) IS NULL OR e.enqNo ILIKE CONCAT('%', CAST(:enqNo AS TEXT), '%'))
    AND (CAST(:companyName AS TEXT) IS NULL OR CAST(:companyName AS TEXT) = '' OR
        COALESCE(c.companyName, e.manualCompanyName, '') ILIKE CONCAT('%', CAST(:companyName AS TEXT), '%'))
    AND (CAST(:lastContactedDate AS DATE) IS NULL OR
        CASE CAST(:dateComparisonTypeLastContacted AS TEXT)
            WHEN '=' THEN e.lastContactedDate = CAST(:lastContactedDate AS DATE)
            WHEN '<' THEN e.lastContactedDate < CAST(:lastContactedDate AS DATE)
            WHEN '>' THEN e.lastContactedDate > CAST(:lastContactedDate AS DATE)
        END)
    AND (CAST(:daysForNextFollowup AS INTEGER) IS NULL OR e.daysForNextFollowup = CAST(:daysForNextFollowup AS INTEGER))
    AND (CAST(:enqDate AS DATE) IS NULL OR
        CASE CAST(:dateComparisonTypeEnqDate AS TEXT)
            WHEN '=' THEN e.enqDate = CAST(:enqDate AS DATE)
            WHEN '<' THEN e.enqDate < CAST(:enqDate AS DATE)
            WHEN '>' THEN e.enqDate > CAST(:enqDate AS DATE)
        END)
    AND (CAST(:closedDate AS DATE) IS NULL OR
        CASE CAST(:dateComparisonTypeClosedDate AS TEXT)
            WHEN '=' THEN e.closedDate = CAST(:closedDate AS DATE)
            WHEN '<' THEN e.closedDate < CAST(:closedDate AS DATE)
            WHEN '>' THEN e.closedDate > CAST(:closedDate AS DATE)
        END)
""")
    Page<Object[]> getActiveEnquiries(
            Pageable pageable,
            @Param("enqNo") String enqNo,
            @Param("companyName") String companyName,
            @Param("lastContactedDate") LocalDate lastContactedDate,
            @Param("daysForNextFollowup") Integer daysForNextFollowup,
            @Param("enqDate") LocalDate enqDate,
            @Param("closedDate") LocalDate closedDate,
            @Param("dateComparisonTypeLastContacted") String dateComparisonTypeLastContacted,
            @Param("dateComparisonTypeEnqDate") String dateComparisonTypeEnqDate,
            @Param("dateComparisonTypeClosedDate") String dateComparisonTypeClosedDate
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

    long countByDeletedDateIsNull();

    @Query("SELECT COUNT(e) FROM Enquiry e WHERE e.status = :status AND e.deletedDate IS NULL")
    long countByStatus(@Param("status") com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus status);

    @Query("SELECT SUM(e.expectedRevenue) FROM Enquiry e WHERE e.deletedDate IS NULL")
    BigDecimal sumExpectedRevenue();

    @Query("SELECT SUM(e.expectedRevenue) FROM Enquiry e WHERE e.status = com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.CONVERTED AND e.deletedDate IS NULL")
    BigDecimal sumWonRevenue();

    @Query("SELECT COUNT(e) FROM Enquiry e WHERE e.nextFollowupDate < CURRENT_DATE AND e.status NOT IN (com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.CONVERTED, com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.LOST, com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.CLOSED) AND e.deletedDate IS NULL")
    long countOverdueFollowups();
}
