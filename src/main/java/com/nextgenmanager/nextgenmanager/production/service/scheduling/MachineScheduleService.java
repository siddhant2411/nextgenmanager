package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.production.dto.MachineScheduleDTO;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderOperationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Operator-facing service for machine task queues.
 * An operator at a specific machine calls this to see what they should work on next.
 */
@Service
@Transactional(readOnly = true)
public class MachineScheduleService {

    private static final Logger logger = LoggerFactory.getLogger(MachineScheduleService.class);

    @Autowired
    private MachineDetailsRepository machineDetailsRepository;

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    /**
     * Get the task queue for a machine within a date range.
     * Tasks are sorted by: priority (URGENT first), then plannedStartDate.
     */
    public MachineScheduleDTO getMachineSchedule(Long machineId, LocalDate from, LocalDate to) {
        MachineDetails machine = machineDetailsRepository.findById(machineId)
                .orElseThrow(() -> new EntityNotFoundException("Machine not found: " + machineId));

        Date fromDate = Date.from(from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
        Date toDate = Date.from(to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());

        List<WorkOrderOperation> operations = workOrderOperationRepository
                .findByAssignedMachineIdAndPlannedStartDateBetweenOrderByPlannedStartDateAsc(
                        machineId, fromDate, toDate);

        return buildScheduleDTO(machine, operations);
    }

    /**
     * Get today's task queue for a machine.
     * Includes tasks that are IN_PROGRESS (carried over) + today's scheduled tasks.
     */
    public MachineScheduleDTO getMachineScheduleToday(Long machineId) {
        MachineDetails machine = machineDetailsRepository.findById(machineId)
                .orElseThrow(() -> new EntityNotFoundException("Machine not found: " + machineId));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Date startOfDay = Date.from(today.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
        Date endOfDay = Date.from(today.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());

        // Get today's scheduled + any in-progress operations on this machine
        List<WorkOrderOperation> scheduled = workOrderOperationRepository
                .findByAssignedMachineIdAndPlannedStartDateBetweenOrderByPlannedStartDateAsc(
                        machineId, startOfDay, endOfDay);

        List<WorkOrderOperation> inProgress = workOrderOperationRepository
                .findByAssignedMachineIdAndStatusOrderByPlannedStartDateAsc(
                        machineId, com.nextgenmanager.nextgenmanager.production.enums.OperationStatus.IN_PROGRESS);

        // Merge and deduplicate
        Set<Long> seenIds = new HashSet<>();
        List<WorkOrderOperation> allOps = new ArrayList<>();
        for (WorkOrderOperation op : inProgress) {
            if (seenIds.add(op.getId())) allOps.add(op);
        }
        for (WorkOrderOperation op : scheduled) {
            if (seenIds.add(op.getId())) allOps.add(op);
        }

        // Sort by priority rank, then planned start date
        allOps.sort((a, b) -> {
            int priorityCompare = getPriorityRank(a) - getPriorityRank(b);
            if (priorityCompare != 0) return priorityCompare;
            if (a.getPlannedStartDate() != null && b.getPlannedStartDate() != null) {
                return a.getPlannedStartDate().compareTo(b.getPlannedStartDate());
            }
            return 0;
        });

        return buildScheduleDTO(machine, allOps);
    }

    private MachineScheduleDTO buildScheduleDTO(MachineDetails machine, List<WorkOrderOperation> operations) {
        MachineScheduleDTO dto = new MachineScheduleDTO();
        dto.setMachineId(machine.getId());
        dto.setMachineCode(machine.getMachineCode());
        dto.setMachineName(machine.getMachineName());
        if (machine.getWorkCenter() != null) {
            dto.setWorkCenterCode(machine.getWorkCenter().getCenterCode());
            dto.setWorkCenterName(machine.getWorkCenter().getCenterName());
        }

        List<MachineScheduleDTO.MachineTask> tasks = operations.stream()
                .map(op -> {
                    MachineScheduleDTO.MachineTask task = new MachineScheduleDTO.MachineTask();
                    task.setOperationId(op.getId());
                    task.setWorkOrderNumber(op.getWorkOrder().getWorkOrderNumber());
                    task.setOperationName(op.getOperationName());
                    task.setSequence(op.getSequence());
                    task.setPlannedQuantity(op.getPlannedQuantity());
                    task.setCompletedQuantity(op.getCompletedQuantity());
                    task.setAvailableInputQuantity(op.getAvailableInputQuantity());
                    task.setStatus(op.getStatus().name());

                    if (op.getWorkOrder().getPriority() != null) {
                        task.setPriority(op.getWorkOrder().getPriority().name());
                    }

                    task.setPlannedStartDate(op.getPlannedStartDate());
                    task.setPlannedEndDate(op.getPlannedEndDate());
                    return task;
                })
                .collect(Collectors.toList());

        dto.setTasks(tasks);
        return dto;
    }

    private int getPriorityRank(WorkOrderOperation op) {
        if (op.getWorkOrder() != null && op.getWorkOrder().getPriority() != null) {
            return op.getWorkOrder().getPriority().getRank();
        }
        return 3; // NORMAL default
    }
}
