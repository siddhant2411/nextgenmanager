package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoiceStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
public class TaxInvoiceDto {
    private Long id;
    private String invoiceNumber;

    private Long salesOrderId;
    private String salesOrderNumber;
    private String customerName;
    private String customerAddress;
    private String customerGstin;
    
    private String deliveryAddress;

    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private TaxInvoiceStatus status;

    private BigDecimal subTotal;
    private BigDecimal discountAmount;
    private BigDecimal taxableValue;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalPayableAmount;
    private BigDecimal paidAmount;

    // Company (Host)
    private String companyName;
    private String companyAddress;
    private String companyGstin;
    private String companyPan;

    private List<TaxInvoiceItemDto> items;
    private List<String> deliveryNoteNumbers;

    private Date creationDate;
    private Date updatedDate;
}
