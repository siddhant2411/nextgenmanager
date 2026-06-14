package com.nextgenmanager.nextgenmanager.accounting.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One party's outstanding split into ageing buckets. Buckets sum to {@code total}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgeingRowDto {
    private Long ledgerId;
    private String code;
    private String partyName;
    private BigDecimal current;     // 0–30 days
    private BigDecimal days31_60;
    private BigDecimal days61_90;
    private BigDecimal days90Plus;
    private BigDecimal total;
}
