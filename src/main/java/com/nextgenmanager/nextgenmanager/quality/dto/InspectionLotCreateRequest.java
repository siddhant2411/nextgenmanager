package com.nextgenmanager.nextgenmanager.quality.dto;

import com.nextgenmanager.nextgenmanager.quality.model.InspectionSource;

import java.math.BigDecimal;
import java.util.List;

/**
 * Raises a lot in PENDING. Which document id is required follows from the source, and the service
 * says so rather than letting a lot exist without the thing it is inspecting.
 *
 * <p>Checks may be listed up front — the inspector's sheet before anyone has written on it — or
 * left out and supplied when the lot is judged.
 */
public record InspectionLotCreateRequest(
        InspectionSource source,
        Integer workOrderId,
        Long workOrderOperationId,
        Long goodsReceiptNoteId,
        Integer inventoryItemId,
        BigDecimal quantityOffered,
        String remarks,
        List<InspectionCheckLine> checks
) {}
