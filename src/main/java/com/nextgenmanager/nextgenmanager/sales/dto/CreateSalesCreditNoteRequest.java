package com.nextgenmanager.nextgenmanager.sales.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateSalesCreditNoteRequest {
    private Integer customerId;         // optional when taxInvoiceId is given (customer derived from invoice)
    private Long taxInvoiceId;          // optional — link to the original invoice

    private LocalDate creditNoteDate;
    private String reason;              // "DEFECTIVE", "SHORT_SUPPLY", "RATE_DIFFERENCE", etc.
    private String remarks;
    private String createdBy;
    private List<SalesCreditNoteLineRequest> items;
}
