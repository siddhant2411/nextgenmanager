package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public record PurchaseOrderCreateDto(
        Integer vendorId,
        PurchaseOrderType poType,
        Date orderDate,
        Date expectedDeliveryDate,
        /** Optional — auto-derived from vendor vs company GSTIN if blank */
        String placeOfSupply,
        String currency,
        BigDecimal exchangeRate,
        String paymentTerms,
        Integer creditDays,
        Integer vendorBillingAddressId,
        Integer shipToAddressId,
        Long salesOrderId,
        String quotationNumber,
        Date quotationDate,
        List<PurchaseOrderItemCreateDto> items,
        String termsAndConditions,
        String internalNotes,
        String remarks
) {}
