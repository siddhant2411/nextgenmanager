package com.nextgenmanager.nextgenmanager.purchase.repository;

import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendorInvoiceRepository extends JpaRepository<VendorInvoice, Long> {

    List<VendorInvoice> findByPurchaseOrderIdAndDeletedDateIsNull(Long poId);

    /**
     * Inward-register feed: every POSTED vendor invoice in the date range, with vendor and line
     * items (HSN-bearing) eagerly fetched. Only POSTED bills have a GL voucher, so the register
     * ties to live Input-GST ledger movement.
     */
    @Query("""
        SELECT DISTINCT vi FROM VendorInvoice vi
        JOIN FETCH vi.vendor
        LEFT JOIN FETCH vi.items it
        LEFT JOIN FETCH it.item
        WHERE vi.invoiceDate BETWEEN :from AND :to
          AND vi.status = com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoiceStatus.POSTED
          AND vi.deletedDate IS NULL
        ORDER BY vi.invoiceDate ASC, vi.invoiceNumber ASC
        """)
    List<VendorInvoice> findForGstRegister(@Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<VendorInvoice> findByIdAndDeletedDateIsNull(Long id);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
