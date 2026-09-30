package com.nextgenmanager.nextgenmanager.quality.service;

import com.nextgenmanager.nextgenmanager.quality.dto.NcrCreateRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.NcrDispositionRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.NonConformanceReportDto;
import com.nextgenmanager.nextgenmanager.quality.model.NcrStatus;

import java.util.List;

/** What happens to goods that failed inspection. */
public interface NonConformanceService {

    List<NonConformanceReportDto> list(NcrStatus status);

    NonConformanceReportDto get(Long id);

    List<NonConformanceReportDto> forLot(Long lotId);

    /** Raises a report against a lot that failed. */
    NonConformanceReportDto raise(NcrCreateRequest request);

    /**
     * Records what is to be done — reworked, scrapped, used as it is, or returned — and moves the
     * affected stock's quality status to match. Closes the report: a decision is a record.
     */
    NonConformanceReportDto decide(Long id, NcrDispositionRequest request);
}
