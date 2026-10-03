package com.nextgenmanager.nextgenmanager.packaging.dto;

import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus;

import java.util.Date;
import java.util.List;

public record PackingSlipDto(
        Long id,
        String slipNumber,
        Long salesOrderId,
        String salesOrderNumber,
        Long pickListId,
        String pickNumber,
        PackingSlipStatus status,
        Date packedDate,
        Date closedDate,
        String packedBy,
        String closedBy,
        String remarks,
        String createdBy,
        Long deliveryNoteId,
        String deliveryNoteNumber,
        List<PackageBoxDto> boxes
) {}
