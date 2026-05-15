package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public record VendorInvoiceDto(
        Long id,
        String invoiceNumber,
        LocalDate invoiceDate,
        Long poId,
        String purchaseOrderNumber,
        Integer vendorId,
        String vendorName,
        Long grnId,
        String grnNumber,
        VendorInvoiceStatus status,
        BigDecimal subtotal,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal cessAmount,
        BigDecimal grandTotal,
        boolean qtyMismatch,
        boolean amountMismatch,
        String remarks,
        String createdBy,
        Date createdDate,
        Date updatedDate,
        List<VendorInvoiceItemDto> items
) {}
