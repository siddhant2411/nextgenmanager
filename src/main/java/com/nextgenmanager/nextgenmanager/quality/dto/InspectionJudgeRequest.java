package com.nextgenmanager.nextgenmanager.quality.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The verdict. Accepted and rejected quantities are the inspector's, not inferred: a part-checked
 * lot has units that are neither yet, and assuming the rest failed would condemn stock nobody
 * looked at.
 */
public record InspectionJudgeRequest(
        BigDecimal quantityAccepted,
        BigDecimal quantityRejected,
        String inspectedBy,
        String remarks,
        List<InspectionCheckLine> checks
) {}
