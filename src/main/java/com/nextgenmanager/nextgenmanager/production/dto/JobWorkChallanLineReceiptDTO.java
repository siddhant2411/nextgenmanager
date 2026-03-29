package com.nextgenmanager.nextgenmanager.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** One line of a receive-back transaction on a Job Work Challan. */
@Data
public class JobWorkChallanLineReceiptDTO {

    @NotNull
    private Long lineId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal quantityReceived;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal quantityRejected = BigDecimal.ZERO;

    private String remarks;
}
