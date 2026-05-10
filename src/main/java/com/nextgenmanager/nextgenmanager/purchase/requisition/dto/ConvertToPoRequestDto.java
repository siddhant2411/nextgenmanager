package com.nextgenmanager.nextgenmanager.purchase.requisition.dto;

import java.util.Date;
import java.util.List;

/**
 * Convert selected PR lines into a single PO for a chosen vendor.
 * Lines must belong to the same PR. Quantities default to remaining.
 */
public record ConvertToPoRequestDto(
        Integer vendorId,
        Date orderDate,
        Date expectedDeliveryDate,
        List<Long> requisitionItemIds
) {}
