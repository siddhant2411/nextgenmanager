package com.nextgenmanager.nextgenmanager.packaging.dto;

import java.math.BigDecimal;
import java.util.List;

public record PackageBoxDto(
        Long id,
        Integer boxNumber,
        String boxType,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal grossWeightKg,
        BigDecimal netWeightKg,
        String shippingMarks,
        List<PackageLineDto> lines
) {}
