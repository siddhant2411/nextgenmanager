package com.nextgenmanager.nextgenmanager.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Date;
import java.util.List;

/** Request body for receiving back materials from the job worker. */
@Data
public class JobWorkChallanReceiptDTO {

    /** Date goods were physically received. Defaults to today if null. */
    private Date receiptDate;

    private String remarks;

    @NotEmpty
    @Valid
    private List<JobWorkChallanLineReceiptDTO> lines;
}
