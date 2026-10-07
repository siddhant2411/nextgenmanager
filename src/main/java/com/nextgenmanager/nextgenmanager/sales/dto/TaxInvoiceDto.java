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

    // Bill to / ship to as issued. customerAddress and deliveryAddress above carry the same two
    // addresses for callers that predate these.
    private String billToName;
    private String billToAddress;
    private String billToGstin;
    private String billToState;                 // "Gujarat (24)"
    private String shipToName;
    private String shipToAddress;
    private String shipToGstin;
    private String shipToState;
    /** True when the goods go to a different name or address than the bill-to. */
    private boolean shipToDiffers;

    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private TaxInvoiceStatus status;

    private BigDecimal subTotal;
    private BigDecimal discountAmount;
    /** Order-level discount %, so a return can be valued at the net rate actually invoiced. */
    private BigDecimal discountPercentage;
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
