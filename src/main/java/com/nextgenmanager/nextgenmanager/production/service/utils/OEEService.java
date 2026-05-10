package com.nextgenmanager.nextgenmanager.production.service.utils;

import com.nextgenmanager.nextgenmanager.production.dto.OEEMetricsDTO;

import java.time.LocalDate;
import java.util.List;

public interface OEEService {
    OEEMetricsDTO getMachineOEE(Long machineId, LocalDate date);
    List<OEEMetricsDTO> getWorkCenterOEE(int workCenterId, LocalDate date);
    List<OEEMetricsDTO> getMachineOEETrend(Long machineId, LocalDate startDate, LocalDate endDate);
}
