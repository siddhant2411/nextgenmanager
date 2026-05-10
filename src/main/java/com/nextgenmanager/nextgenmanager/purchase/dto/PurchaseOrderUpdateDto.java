package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** All fields are optional — only non-null values are applied. Allowed in DRAFT status only. */
public record PurchaseOrderUpdateDto(
        Integer vendorId,
        PurchaseOrderType poType,
        Date orderDate,
        Date expectedDeliveryDate,
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
