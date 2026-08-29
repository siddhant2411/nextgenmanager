package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
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
        /**
         * Where this PO came from outside the ERP -- a spreadsheet row, a vendor portal id, an
         * email thread. Importers use it as the idempotency key; it is not required to be unique.
         */
        String reference,
        /**
         * Optional override for the GST treatment. Left null, it is derived by comparing the
         * vendor's GSTIN state with the company's, which yields UNREGISTERED -- and therefore
         * zero tax -- for any vendor with no GSTIN on file. Set it explicitly when you know tax
         * was charged and the vendor's registration simply is not recorded, e.g. when loading
         * historical purchases.
         */
        GstTreatment gstTreatment,
        List<PurchaseOrderItemCreateDto> items,
        String termsAndConditions,
        String internalNotes,
        String remarks
) {}
