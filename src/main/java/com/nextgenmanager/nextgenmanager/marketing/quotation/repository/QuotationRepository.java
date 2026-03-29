package com.nextgenmanager.nextgenmanager.marketing.quotation.repository;

import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    @Query(value = "select * from quotation q where q.id=:id AND q.deletedDate IS NULL", nativeQuery = true)
    public Quotation findByActiveId(Long id);


    List<Quotation> findByEnquiryId(Long enquiryId);

    @Query(nativeQuery = true, value = """
    SELECT 
        q.id AS id, 
        q.qtnNo AS qtnNo, 
        q.qtnDate AS qtnDate, 
        e.enqNo AS enqNo, 
        e.enqDate AS enqDate,
        c.companyName AS companyName, 
        q.netAmount AS netAmount, 
        q.totalAmount AS totalAmount 
    FROM 
        quotation q
    INNER JOIN 
        enquiry e ON q.enquiry_id = e.id
    INNER JOIN 
        contact c ON e.contact_id = c.id
    WHERE 
        (:companyName IS NULL OR c.companyName = :companyName)
        AND (:qtnNo IS NULL OR q.qtnNo ILIKE CONCAT('%', CAST(:qtnNo AS TEXT), '%'))
        AND (CAST(:qtnDate AS DATE) IS NULL OR q.qtnDate = CAST(:qtnDate AS DATE) ) 
        AND (CAST(:enqDate AS DATE) IS NULL OR e.enqDate = CAST(:enqDate AS DATE) ) 
        AND (:enqNo IS NULL OR e.enqNo = :enqNo)
        AND (:netAmount IS NULL OR q.netAmount = :netAmount)
        AND (:totalAmount IS NULL OR q.totalAmount = :totalAmount)
        AND q.deletedDate IS NULL
    """)
    Page<Object[]> getActiveQuotation(
            Pageable pageable,
            @Param("companyName") String companyName,
            @Param("qtnNo") String qtnNo,
            @Param("qtnDate") LocalDate qtnDate,
            @Param("enqDate") LocalDate enqDate,
            @Param("enqNo") String enqNo,
            @Param("netAmount") BigDecimal netAmount,
            @Param("totalAmount") BigDecimal totalAmount
    );

}
