package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.dto.CreateSalesCreditNoteRequest;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesCreditNoteListDTO;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesCreditNoteResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SalesCreditNoteService {

    /** Create a Sales Credit Note in DRAFT status. No GL posting yet. */
    SalesCreditNoteResponseDTO create(CreateSalesCreditNoteRequest request);

    /** Confirm a DRAFT note — publishes the event that auto-posts the CREDIT_NOTE voucher. */
    SalesCreditNoteResponseDTO confirm(Long id);

    /** Cancel a DRAFT or CONFIRMED note. */
    SalesCreditNoteResponseDTO cancel(Long id);

    SalesCreditNoteResponseDTO getById(Long id);

    Page<SalesCreditNoteListDTO> getAll(Pageable pageable);

    List<SalesCreditNoteListDTO> getByCustomer(int customerId);

    List<SalesCreditNoteListDTO> getByInvoice(Long taxInvoiceId);

    /** Preview next credit note number without consuming the sequence. */
    String nextNumber();
}
