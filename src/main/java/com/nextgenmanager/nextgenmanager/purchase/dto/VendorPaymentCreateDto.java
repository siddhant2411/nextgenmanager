package com.nextgenmanager.nextgenmanager.purchase.dto;

import com.nextgenmanager.nextgenmanager.sales.model.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class VendorPaymentCreateDto {

    @NotNull
    private LocalDate paymentDate;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    private PaymentMode paymentMode;

    private String referenceNumber;
    private String notes;

    // ── Optional TDS withheld on this payment (Phase 4). Net bank outflow = amount − tdsAmount. ──
    private String tdsSectionCode;
    private BigDecimal tdsRate;

    @DecimalMin(value = "0.00")
    private BigDecimal tdsAmount;
}
