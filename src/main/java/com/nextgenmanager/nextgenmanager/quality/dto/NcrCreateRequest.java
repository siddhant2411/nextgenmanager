package com.nextgenmanager.nextgenmanager.quality.dto;

import java.math.BigDecimal;

/**
 * Raises a report against a lot that failed. The problem is required: a non-conformance that
 * cannot say what is wrong gives whoever picks it up nothing to act on.
 */
public record NcrCreateRequest(
        Long inspectionLotId,
        BigDecimal quantity,
        String problem,
        String raisedBy,
        String remarks
) {}
