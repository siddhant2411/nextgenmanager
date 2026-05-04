package com.nextgenmanager.nextgenmanager.company.service;

import com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsDTO;
import com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsRequestDTO;

public interface CompanyDetailsService {

    /** Returns the current company details, or an empty DTO with defaults if none saved yet. */
    CompanyDetailsDTO get();

    /** Creates or updates the single company details record. */
    CompanyDetailsDTO upsert(CompanyDetailsRequestDTO request);
}
