package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

public record VendorInvoiceListDto(
        Long id,
        String invoiceNumber,
        LocalDate invoiceDate,
        Long poId,
        String purchaseOrderNumber,
        Integer vendorId,
        String vendorName,
        VendorInvoiceStatus status,
        BigDecimal grandTotal,
        boolean qtyMismatch,
        boolean amountMismatch,
        Date createdDate
) {}
