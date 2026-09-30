package com.nextgenmanager.nextgenmanager.quality.dto;

import com.nextgenmanager.nextgenmanager.quality.model.NcrDisposition;
import com.nextgenmanager.nextgenmanager.quality.model.NcrStatus;

import java.math.BigDecimal;
import java.util.Date;

/** A non-conformance and what was decided about it. */
public record NonConformanceReportDto(
        Long id,
        String ncrNumber,
        Long inspectionLotId,
        String inspectionLotNumber,
        String itemCode,
        String itemName,
        BigDecimal quantity,
        String problem,
        NcrDisposition disposition,
        String dispositionedBy,
        Date dispositionedDate,
        String dispositionNotes,
        String approvedBy,
        NcrStatus status,
        String raisedBy,
        String remarks,
        Date creationDate
) {}
