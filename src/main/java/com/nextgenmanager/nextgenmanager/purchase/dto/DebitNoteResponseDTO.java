package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.purchase.model.DebitNoteStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.ReturnReason;

import java.time.LocalDate;
import java.util.List;

public record DebitNoteResponseDTO(
        Long id,
        String debitNoteNumber,
        LocalDate debitNoteDate,
        int vendorId,
        String vendorName,
        Long purchaseOrderId,
        String purchaseOrderNumber,
        Long grnId,
        String grnNumber,
        ReturnReason returnReason,
        DebitNoteStatus status,
        String remarks,
        double subtotal,
        double totalGstAmount,
        double totalAmount,
        String createdBy,
        LocalDate createdDate,
        List<DebitNoteItemDTO> items
) {}
