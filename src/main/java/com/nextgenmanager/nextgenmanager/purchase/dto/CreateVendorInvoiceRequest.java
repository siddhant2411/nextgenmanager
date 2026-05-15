package com.nextgenmanager.nextgenmanager.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateVendorInvoiceRequest(
        Long poId,
        Long grnId,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal subtotal,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal cessAmount,
        BigDecimal grandTotal,
        String remarks,
        List<VendorInvoiceItemRequest> items
) {}
