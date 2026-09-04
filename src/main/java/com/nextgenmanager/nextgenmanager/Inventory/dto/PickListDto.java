package com.nextgenmanager.nextgenmanager.Inventory.dto;

import com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus;

import java.util.Date;
import java.util.List;

public record PickListDto(
        Long id,
        String pickNumber,
        Long salesOrderId,
        String salesOrderNumber,
        Long warehouseId,
        String warehouseCode,
        PickListStatus status,
        Date releasedDate,
        Date pickedDate,
        String pickedBy,
        String remarks,
        String createdBy,
        /** The delivery note that shipped this pick, once one has. */
        Long deliveryNoteId,
        String deliveryNoteNumber,
        List<PickListLineDto> lines
) {}
