package com.nextgenmanager.nextgenmanager.company.dto;

import java.util.Date;

public record CompanyDetailsDTO(
        Long id,
        String companyName,
        String legalName,
        String tradeName,
        String gstNumber,
        String panNumber,
        String tan,
        String cinNumber,
        String phone,
        String email,
        String website,
        String street1,
        String street2,
        String city,
        String state,
        String pinCode,
        String country,
        String currency,
        Integer financialYearStartMonth,
        String notes,
        String bankName,
        String bankAccountNumber,
        String bankIfscCode,
        String bankBranch,
        Date creationDate,
        Date updatedDate
) {}
