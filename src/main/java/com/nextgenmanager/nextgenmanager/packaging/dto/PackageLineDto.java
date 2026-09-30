package com.nextgenmanager.nextgenmanager.packaging.dto;

import java.math.BigDecimal;
import java.util.List;

public record PackageLineDto(
        Long id,
        int inventoryItemId,
        String itemCode,
        String itemName,
        Long pickListLineId,
        BigDecimal quantity,
        List<Long> instanceIds
) {}
