package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.sales.model.PaymentMode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Data
public class VendorPaymentDto {
    private Long id;
    private Long vendorInvoiceId;
    private String invoiceNumber;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private PaymentMode paymentMode;
    private String referenceNumber;
    private String notes;
    private String tdsSectionCode;
    private BigDecimal tdsRate;
    private BigDecimal tdsAmount;
    private Date creationDate;
}
