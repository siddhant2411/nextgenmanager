package com.nextgenmanager.nextgenmanager.sales.repository;

import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNote;
import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesCreditNoteRepository extends JpaRepository<SalesCreditNote, Long> {

    Optional<SalesCreditNote> findByIdAndDeletedDateIsNull(Long id);

    /** Outward-register / GSTR-1 CDNR feed: confirmed credit notes in a date range (customer is EAGER). */
    List<SalesCreditNote> findByStatusAndCreditNoteDateBetweenAndDeletedDateIsNullOrderByCreditNoteDateAscCreditNoteNumberAsc(
            SalesCreditNoteStatus status, LocalDate from, LocalDate to);

    Page<SalesCreditNote> findByDeletedDateIsNull(Pageable pageable);

    List<SalesCreditNote> findByCustomer_IdAndDeletedDateIsNull(int customerId);

    List<SalesCreditNote> findByTaxInvoice_IdAndDeletedDateIsNull(Long taxInvoiceId);

    boolean existsByCreditNoteNumber(String creditNoteNumber);
}
