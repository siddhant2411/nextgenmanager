package com.nextgenmanager.nextgenmanager.sales.repository;

import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaxInvoiceRepository extends JpaRepository<TaxInvoice, Long> {

    List<TaxInvoice> findBySalesOrderIdAndDeletedDateIsNull(Long salesOrderId);

    /**
     * Outward-register feed: every non-cancelled tax invoice in the date range, with its
     * customer (and line items) eagerly fetched. Cancelled invoices are excluded because their
     * GL voucher has been reversed — the register must tie to live ledger movement.
     */
    @Query("""
        SELECT DISTINCT ti FROM TaxInvoice ti
        JOIN FETCH ti.salesOrder so
        JOIN FETCH so.customer
        LEFT JOIN FETCH ti.items it
        LEFT JOIN FETCH it.inventoryItem
        WHERE ti.invoiceDate BETWEEN :from AND :to
          AND ti.status <> com.nextgenmanager.nextgenmanager.sales.model.TaxInvoiceStatus.CANCELLED
          AND ti.deletedDate IS NULL
        ORDER BY ti.invoiceDate ASC, ti.invoiceNumber ASC
        """)
    List<TaxInvoice> findForGstRegister(@Param("from") LocalDate from, @Param("to") LocalDate to);

    Page<TaxInvoice> findByDeletedDateIsNull(Pageable pageable);

    Page<TaxInvoice> findByStatusAndDeletedDateIsNull(TaxInvoiceStatus status, Pageable pageable);

    Optional<TaxInvoice> findByIdAndDeletedDateIsNull(Long id);
}
