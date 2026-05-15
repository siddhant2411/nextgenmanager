package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderType;

import java.math.BigDecimal;
import java.util.Date;

/** Lightweight projection for list views — avoids loading items. */
public record PurchaseOrderListDto(
        Long id,
        String purchaseOrderNumber,
        PurchaseOrderType poType,
        Integer vendorId,
        String vendorName,
        Date orderDate,
        Date expectedDeliveryDate,
        PurchaseOrderStatus status,
        PurchaseOrderApprovalStatus approvalStatus,
        String currency,
        BigDecimal grandTotal,
        int itemCount,
        Date createdDate,
        Integer daysOverdue
) {}
