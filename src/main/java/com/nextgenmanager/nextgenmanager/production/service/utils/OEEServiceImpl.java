package com.nextgenmanager.nextgenmanager.production.service.utils;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineProductionLog;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineProductionLogRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.production.dto.OEEMetricsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OEEServiceImpl implements OEEService {

    private final MachineProductionLogRepository machineProductionLogRepository;
    private final MachineDetailsRepository machineDetailsRepository;

    @Override
    public OEEMetricsDTO getMachineOEE(Long machineId, LocalDate date) {
        MachineDetails machine = machineDetailsRepository.findById(machineId)
                .orElseThrow(() -> new ResourceNotFoundException("Machine not found"));

        // Sum up logs for all shifts on that date
        List<MachineProductionLog> logs = machineProductionLogRepository.findByMachineIdAndProductionDate(machineId, date);
        
        return calculateMetrics(machine, date, logs);
    }

    @Override
    public List<OEEMetricsDTO> getWorkCenterOEE(int workCenterId, LocalDate date) {
        List<MachineDetails> machines = machineDetailsRepository.findByWorkCenterId(workCenterId);
        return machines.stream()
                .map(m -> getMachineOEE(m.getId(), date))
                .collect(Collectors.toList());
    }

    @Override
    public List<OEEMetricsDTO> getMachineOEETrend(Long machineId, LocalDate startDate, LocalDate endDate) {
        MachineDetails machine = machineDetailsRepository.findById(machineId)
                .orElseThrow(() -> new ResourceNotFoundException("Machine not found"));

        return startDate.datesUntil(endDate.plusDays(1))
                .map(date -> getMachineOEE(machineId, date))
                .collect(Collectors.toList());
    }

    private OEEMetricsDTO calculateMetrics(MachineDetails machine, LocalDate date, List<MachineProductionLog> logs) {
        int totalRuntime = 0;
        int totalDowntime = 0;
        int totalActual = 0;
        int totalRejected = 0;
        int totalPlanned = 0;

        for (MachineProductionLog log : logs) {
            totalRuntime += log.getRuntimeMinutes() != null ? log.getRuntimeMinutes() : 0;
            totalDowntime += log.getDowntimeMinutes() != null ? log.getDowntimeMinutes() : 0;
            totalActual += log.getActualQuantity() != null ? log.getActualQuantity() : 0;
            totalRejected += log.getRejectedQuantity() != null ? log.getRejectedQuantity() : 0;
            totalPlanned += log.getPlannedQuantity() != null ? log.getPlannedQuantity() : 0;
        }

        BigDecimal availability = calculateAvailability(totalRuntime, totalDowntime);
        BigDecimal performance = calculatePerformance(totalActual, totalPlanned);
        BigDecimal quality = calculateQuality(totalActual, totalRejected);
        BigDecimal oee = availability.multiply(performance).multiply(quality).setScale(4, RoundingMode.HALF_UP);

        return OEEMetricsDTO.builder()
                .machineId(machine.getId())
                .machineName(machine.getMachineName())
                .date(date)
                .availability(availability)
                .performance(performance)
                .quality(quality)
                .oee(oee)
                .runtimeMinutes(totalRuntime)
                .downtimeMinutes(totalDowntime)
                .actualQuantity(totalActual)
                .rejectedQuantity(totalRejected)
                .plannedQuantity(totalPlanned)
                .build();
    }

    private BigDecimal calculateAvailability(int runtime, int downtime) {
        int total = runtime + downtime;
        if (total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(runtime).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePerformance(int actual, int planned) {
        if (planned == 0) return actual > 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        return BigDecimal.valueOf(actual).divide(BigDecimal.valueOf(planned), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateQuality(int actual, int rejected) {
        if (actual == 0) return BigDecimal.ZERO;
        int good = actual - rejected;
        return BigDecimal.valueOf(good).divide(BigDecimal.valueOf(actual), 4, RoundingMode.HALF_UP);
    }
}
