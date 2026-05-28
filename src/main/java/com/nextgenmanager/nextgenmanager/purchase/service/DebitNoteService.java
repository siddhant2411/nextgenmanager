package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.purchase.dto.CreateDebitNoteRequest;
import com.nextgenmanager.nextgenmanager.purchase.dto.DebitNoteListDTO;
import com.nextgenmanager.nextgenmanager.purchase.dto.DebitNoteResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DebitNoteService {

    /** Create a Debit Note in DRAFT status. No stock movement yet. */
    DebitNoteResponseDTO create(CreateDebitNoteRequest request);

    /** Confirm a DRAFT note — triggers PURCHASE_RETURN stock adjustment (negative qty). */
    DebitNoteResponseDTO confirm(Long id);

    /** Cancel a DRAFT or CONFIRMED note. If CONFIRMED, reverses the stock adjustment. */
    DebitNoteResponseDTO cancel(Long id);

    DebitNoteResponseDTO getById(Long id);

    Page<DebitNoteListDTO> getAll(Pageable pageable);

    List<DebitNoteListDTO> getByVendor(int vendorId);

    List<DebitNoteListDTO> getByGrn(Long grnId);

    List<DebitNoteListDTO> getByPurchaseOrder(Long poId);

    /** Preview next debit note number without consuming the sequence. */
    String nextNumber();
}
