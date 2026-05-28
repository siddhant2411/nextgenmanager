package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.purchase.model.DebitNoteStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.ReturnReason;

import java.time.LocalDate;

public record DebitNoteListDTO(
        Long id,
        String debitNoteNumber,
        LocalDate debitNoteDate,
        String vendorName,
        String purchaseOrderNumber,
        String grnNumber,
        ReturnReason returnReason,
        DebitNoteStatus status,
        double totalAmount,
        LocalDate createdDate
) {}
