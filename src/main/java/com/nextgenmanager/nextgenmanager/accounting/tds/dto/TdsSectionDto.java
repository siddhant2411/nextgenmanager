package com.nextgenmanager.nextgenmanager.accounting.tds.dto;

import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsApplicableTo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TdsSectionDto {
    private Long id;
    private String section;
    private String description;
    private BigDecimal rate;
    private BigDecimal panMissingRate;
    private BigDecimal thresholdSingle;
    private BigDecimal thresholdAnnual;
    private TdsApplicableTo applicableTo;
    private boolean active;
}
