package com.nextgenmanager.nextgenmanager.quality.dto;

import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionSource;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** An inspection and its verdict. */
public record InspectionLotDto(
        Long id,
        String lotNumber,
        InspectionSource source,
        InspectionLotStatus status,
        int inventoryItemId,
        String itemCode,
        String itemName,
        Integer workOrderId,
        String workOrderNumber,
        Long workOrderOperationId,
        Long goodsReceiptNoteId,
        BigDecimal quantityOffered,
        BigDecimal quantityAccepted,
        BigDecimal quantityRejected,
        String inspectedBy,
        Date inspectedDate,
        String waivedBy,
        String waiverReason,
        String remarks,
        String createdBy,
        List<InspectionResultDto> results
) {}
