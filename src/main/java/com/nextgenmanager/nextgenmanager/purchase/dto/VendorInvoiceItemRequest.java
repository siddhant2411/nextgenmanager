package com.nextgenmanager.nextgenmanager.purchase.dto;

import java.math.BigDecimal;

public record VendorInvoiceItemRequest(
        Long itemId,
        String hsnCode,
        String uom,
        double invoicedQty,
        BigDecimal unitPrice,
        BigDecimal taxableValue,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal cessAmount,
        BigDecimal lineTotal
) {}
