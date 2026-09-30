package com.nextgenmanager.nextgenmanager.quality.dto;

import java.math.BigDecimal;

/**
 * One line of an inspector's sheet, going in. A check with no observation yet is a check waiting
 * to be made; one with an observation is the answer.
 */
public record InspectionCheckLine(
        Long id,
        String parameterName,
        String parameterType,
        BigDecimal minValue,
        BigDecimal maxValue,
        String unit,
        Boolean critical,
        BigDecimal observedValue,
        String observedText,
        Boolean passed,
        String remarks
) {}
