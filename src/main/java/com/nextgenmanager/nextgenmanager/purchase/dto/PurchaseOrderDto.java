package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** Full PO response — returned by GET /{id} and after create/update. */
public record PurchaseOrderDto(
        Long id,
        String purchaseOrderNumber,
        PurchaseOrderType poType,

        // Vendor
        Integer vendorId,
        String vendorName,
        String vendorGstin,
        String vendorEmail,
        String vendorPhone,
        String vendorBillingAddressId,

        // Delivery
        Integer shipToAddressId,
        Date orderDate,
        Date expectedDeliveryDate,

        // Status
        PurchaseOrderStatus status,
        PurchaseOrderApprovalStatus approvalStatus,
        String approvedBy,
        Date approvedDate,
        String rejectionReason,

        // GST
        GstTreatment gstTreatment,
        String placeOfSupply,

        // Commercial
        String currency,
        BigDecimal exchangeRate,
        String paymentTerms,
        Integer creditDays,

        // Totals
        BigDecimal subtotal,
        BigDecimal totalDiscount,
        BigDecimal taxableValue,
        BigDecimal cgstAmount,
        BigDecimal sgstAmount,
        BigDecimal igstAmount,
        BigDecimal cessAmount,
        BigDecimal roundOff,
        BigDecimal grandTotal,
        String grandTotalInWords,

        // Quotation reference
        String quotationNumber,
        Date quotationDate,

        // Email tracking
        Date sentToVendorAt,
        String sentToVendorEmail,

        // Metadata
        Integer revisionNo,
        Long parentPoId,
        Long salesOrderId,
        String termsAndConditions,
        String internalNotes,
        String remarks,
        Date createdDate,
        Date updatedDate,

        List<PurchaseOrderItemDto> items
) {}
