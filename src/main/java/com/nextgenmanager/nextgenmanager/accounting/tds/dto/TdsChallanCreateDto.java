package com.nextgenmanager.nextgenmanager.accounting.tds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TdsChallanCreateDto {

    @NotBlank
    private String financialYear;

    @NotBlank
    @Pattern(regexp = "Q[1-4]", message = "quarter must be Q1..Q4")
    private String quarter;

    /** Optional: restrict the challan to a single section; null = all pending sections in the quarter. */
    private String section;

    @NotBlank
    private String challanNumber;

    private String bsrCode;

    @NotNull
    private LocalDate depositDate;

    private String notes;
}
