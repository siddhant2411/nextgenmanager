package com.nextgenmanager.nextgenmanager.accounting.gst.filing.dto;

import java.math.BigDecimal;
import java.util.Date;

/** A filed GST return. {@code payload} (the full JSON snapshot) is populated only on single fetch. */
public record GstReturnDto(
        Long id,
        String returnType,
        String status,
        String periodLabel,
        Long periodId,
        Long financialYearId,
        BigDecimal taxableValue,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal cess,
        BigDecimal total,
        String filedBy,
        Date filedDate,
        String payload
) {}
