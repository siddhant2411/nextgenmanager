package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNoteStatus;

import java.time.LocalDate;

public record SalesCreditNoteListDTO(
        Long id,
        String creditNoteNumber,
        LocalDate creditNoteDate,
        String customerName,
        String taxInvoiceNumber,
        String reason,
        SalesCreditNoteStatus status,
        double totalAmount,
        LocalDate createdDate
) {}
