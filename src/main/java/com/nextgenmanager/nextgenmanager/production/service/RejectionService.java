package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.DispositionRequestDTO;
import com.nextgenmanager.nextgenmanager.production.dto.RejectionEntryDTO;
import com.nextgenmanager.nextgenmanager.production.dto.YieldMetricsDTO;
import com.nextgenmanager.nextgenmanager.production.enums.DispositionStatus;

import java.util.List;

public interface RejectionService {

    void disposeRejection(DispositionRequestDTO dto);

    List<RejectionEntryDTO> listRejections(int workOrderId, DispositionStatus statusFilter);

    YieldMetricsDTO getYieldMetrics(int workOrderId);
}
