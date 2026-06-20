package com.nextgenmanager.nextgenmanager.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyDetailsRequestDTO(
        @NotBlank(message = "Company name is required")
        @Size(max = 200)
        String companyName,

        @Size(max = 200)
        String legalName,

        @Size(max = 200)
        String tradeName,

        @Size(max = 15, message = "GSTIN must be 15 characters or less")
        String gstNumber,

        @Size(max = 10, message = "PAN must be 10 characters or less")
        String panNumber,

        @Size(max = 15, message = "TAN must be 15 characters or less")
        String tan,

        @Size(max = 21, message = "CIN must be 21 characters or less")
        String cinNumber,

        @Size(max = 20)
        String phone,

        @Size(max = 150)
        String email,

        @Size(max = 200)
        String website,

        @Size(max = 200)
        String street1,

        @Size(max = 200)
        String street2,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String state,

        @Size(max = 10)
        String pinCode,

        @Size(max = 100)
        String country,

        @Size(max = 3)
        String currency,

        Integer financialYearStartMonth,

        String notes,

        @Size(max = 200)
        String bankName,

        @Size(max = 30)
        String bankAccountNumber,

        @Size(max = 20)
        String bankIfscCode,

        @Size(max = 200)
        String bankBranch
) {}
