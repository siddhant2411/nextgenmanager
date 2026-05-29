package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.production.dto.OEEMetricsDTO;
import com.nextgenmanager.nextgenmanager.production.service.utils.OEEService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresProductionModuleAccess;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/production/analytics/oee")
@RequiredArgsConstructor
@RequiresProductionModuleAccess
public class OEEDashboardController {

    private final OEEService oeeService;

    @GetMapping("/machine/{machineId}")
    public OEEMetricsDTO getMachineOEE(
            @PathVariable Long machineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) date = LocalDate.now();
        return oeeService.getMachineOEE(machineId, date);
    }

    @GetMapping("/work-center/{workCenterId}")
    public List<OEEMetricsDTO> getWorkCenterOEE(
            @PathVariable int workCenterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) date = LocalDate.now();
        return oeeService.getWorkCenterOEE(workCenterId, date);
    }

    @GetMapping("/machine/{machineId}/trend")
    public List<OEEMetricsDTO> getMachineTrend(
            @PathVariable Long machineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return oeeService.getMachineOEETrend(machineId, startDate, endDate);
    }
}
