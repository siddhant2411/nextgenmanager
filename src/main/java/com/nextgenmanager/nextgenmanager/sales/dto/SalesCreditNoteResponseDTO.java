package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNoteStatus;

import java.time.LocalDate;
import java.util.List;

public record SalesCreditNoteResponseDTO(
        Long id,
        String creditNoteNumber,
        LocalDate creditNoteDate,
        int customerId,
        String customerName,
        Long taxInvoiceId,
        String taxInvoiceNumber,
        String reason,
        SalesCreditNoteStatus status,
        String remarks,
        double subtotal,
        double totalGstAmount,
        double totalAmount,
        String createdBy,
        LocalDate createdDate,
        List<SalesCreditNoteItemDTO> items
) {}
