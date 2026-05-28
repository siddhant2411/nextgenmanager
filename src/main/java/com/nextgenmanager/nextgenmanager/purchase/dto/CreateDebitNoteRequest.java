package com.nextgenmanager.nextgenmanager.purchase.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateDebitNoteRequest {
    private int vendorId;
    private Long purchaseOrderId;          // optional — link to PO
    private Long grnId;                    // optional — link to GRN
    private LocalDate debitNoteDate;
    private String returnReason;           // "DEFECTIVE", "EXCESS_QUANTITY", etc.
    private String remarks;
    private String createdBy;
    private List<DebitNoteLineRequest> items;
}
