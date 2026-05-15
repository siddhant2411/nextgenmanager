package com.nextgenmanager.nextgenmanager.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TaxInvoiceCreateDto {

    @NotNull
    private Long salesOrderId;

    private LocalDate invoiceDate;

    private LocalDate dueDate;

    private BigDecimal paidAmount;
    private String invoiceNumber;
}
