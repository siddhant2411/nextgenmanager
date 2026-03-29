package com.nextgenmanager.nextgenmanager.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class JobWorkChallanRequestDTO {

    /** Job worker / subcontractor contact ID. Must be a VENDOR or BOTH contact. */
    @NotNull(message = "Vendor is required")
    private Integer vendorId;

    /** Optional — link to Work Order. */
    private Long workOrderId;

    /** Optional — link to specific SUB_CONTRACTED WorkOrderOperation. */
    private Long workOrderOperationId;

    /** Agreed job-work rate per unit (reference only). */
    private BigDecimal agreedRatePerUnit;

    /** Courier / transport details. */
    private String dispatchDetails;

    private String remarks;

    @NotEmpty(message = "At least one material line is required")
    @Valid
    private List<JobWorkChallanLineRequestDTO> lines;
}
