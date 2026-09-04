package com.nextgenmanager.nextgenmanager.quality.dto;

import com.nextgenmanager.nextgenmanager.production.enums.QaResult;

import java.math.BigDecimal;

/** One check on a lot, as the API exposes it. */
public record InspectionResultDto(
        Long id,
        String parameterName,
        String parameterType,
        BigDecimal minValue,
        BigDecimal maxValue,
        String unit,
        boolean critical,
        BigDecimal observedValue,
        String observedText,
        QaResult result,
        String remarks
) {}
