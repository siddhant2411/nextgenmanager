package com.nextgenmanager.nextgenmanager.accounting.tds.dto;

import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsApplicableTo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TdsSectionCreateDto {

    @NotBlank
    private String section;

    @NotBlank
    private String description;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal rate;

    @DecimalMin("0.0")
    private BigDecimal panMissingRate;

    private BigDecimal thresholdSingle;
    private BigDecimal thresholdAnnual;
    private TdsApplicableTo applicableTo;
    private Boolean active;
}
