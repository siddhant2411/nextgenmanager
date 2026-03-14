package com.nextgenmanager.nextgenmanager.production.service.scheduling;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.production.dto.ProductionScheduleDTO;
import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderOperation;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderOperationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductionScheduleViewService {

    @Autowired
    private WorkOrderOperationRepository workOrderOperationRepository;

    public ProductionScheduleDTO getCombinedSchedule(LocalDate from, LocalDate to) {
        Date fromDate = Date.from(from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
        Date toDate = Date.from(to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());

        List<WorkOrderOperation> operations = workOrderOperationRepository.findScheduleByDateRangeAndStatuses(
                fromDate, toDate, Arrays.asList(OperationStatus.PLANNED, OperationStatus.READY, OperationStatus.IN_PROGRESS, OperationStatus.COMPLETED)
        );

        return buildDto(fromDate, toDate, operations);
    }

    public ProductionScheduleDTO getWorkCenterSchedule(Integer workCenterId, LocalDate from, LocalDate to) {
        Date fromDate = Date.from(from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
        Date toDate = Date.from(to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());

        List<WorkOrderOperation> operations = workOrderOperationRepository.findScheduleByWorkCenter(
                workCenterId, fromDate, toDate
        );

        return buildDto(fromDate, toDate, operations);
    }

    public ProductionScheduleDTO getMachineSchedule(Long machineId, LocalDate from, LocalDate to) {
        Date fromDate = Date.from(from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());
        Date toDate = Date.from(to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant());

        List<WorkOrderOperation> operations = workOrderOperationRepository.findScheduleByMachine(
                machineId, fromDate, toDate
        );

        return buildDto(fromDate, toDate, operations);
    }

    private ProductionScheduleDTO buildDto(Date from, Date to, List<WorkOrderOperation> operations) {
        ProductionScheduleDTO dto = new ProductionScheduleDTO();
        dto.setFromDate(from);
        dto.setToDate(to);

        int totalOps = 0;
        int completedOps = 0;
        int inProgressOps = 0;
        BigDecimal totalPlannedMins = BigDecimal.ZERO;
        BigDecimal totalCompletedMins = BigDecimal.ZERO;

        Map<WorkCenter, List<WorkOrderOperation>> wcMap = new HashMap<>();

        for (WorkOrderOperation op : operations) {
            totalOps++;
            if (op.getStatus() == OperationStatus.COMPLETED) {
                completedOps++;
                totalCompletedMins = totalCompletedMins.add(getDuration(op));
            } else if (op.getStatus() == OperationStatus.IN_PROGRESS) {
                inProgressOps++;
            }
            totalPlannedMins = totalPlannedMins.add(getDuration(op));

            WorkCenter wc = op.getWorkCenter();
            if (wc != null) {
                wcMap.computeIfAbsent(wc, k -> new ArrayList<>()).add(op);
            }
        }

        List<ProductionScheduleDTO.WorkCenterSchedule> wcSchedules = new ArrayList<>();
        for (Map.Entry<WorkCenter, List<WorkOrderOperation>> entry : wcMap.entrySet()) {
            WorkCenter wc = entry.getKey();
            List<WorkOrderOperation> wcOps = entry.getValue();

            ProductionScheduleDTO.WorkCenterSchedule wcSched = new ProductionScheduleDTO.WorkCenterSchedule();
            wcSched.setWorkCenterId((long) wc.getId());
            wcSched.setWorkCenterCode(wc.getCenterCode());
            wcSched.setWorkCenterName(wc.getCenterName());

            Map<MachineDetails, List<WorkOrderOperation>> machineMap = new HashMap<>();
            List<ProductionScheduleDTO.ScheduledOperation> wcDirectOps = new ArrayList<>();

            int wcTotalOps = 0;
            int wcCompletedOps = 0;
            BigDecimal wcTotalPlannedMins = BigDecimal.ZERO;

            for (WorkOrderOperation op : wcOps) {
                wcTotalOps++;
                if (op.getStatus() == OperationStatus.COMPLETED) {
                    wcCompletedOps++;
                }
                wcTotalPlannedMins = wcTotalPlannedMins.add(getDuration(op));

                if (op.getAssignedMachine() != null) {
                    machineMap.computeIfAbsent(op.getAssignedMachine(), k -> new ArrayList<>()).add(op);
                } else {
                    wcDirectOps.add(mapToDto(op));
                }
            }

            wcSched.setOperations(wcDirectOps);

            List<ProductionScheduleDTO.MachineScheduleBlock> machineBlocks = new ArrayList<>();
            for (Map.Entry<MachineDetails, List<WorkOrderOperation>> mEntry : machineMap.entrySet()) {
                MachineDetails machine = mEntry.getKey();
                List<WorkOrderOperation> mOps = mEntry.getValue();

                ProductionScheduleDTO.MachineScheduleBlock mBlock = new ProductionScheduleDTO.MachineScheduleBlock();
                mBlock.setMachineId(machine.getId());
                mBlock.setMachineCode(machine.getMachineCode());
                mBlock.setMachineName(machine.getMachineName());
                if (machine.getMachineStatus() != null) {
                    mBlock.setMachineStatus(machine.getMachineStatus().name());
                }

                int mTotalOps = 0;
                BigDecimal mTotalPlannedMins = BigDecimal.ZERO;
                List<ProductionScheduleDTO.ScheduledOperation> machineOpDtos = new ArrayList<>();

                for (WorkOrderOperation op : mOps) {
                    mTotalOps++;
                    mTotalPlannedMins = mTotalPlannedMins.add(getDuration(op));
                    machineOpDtos.add(mapToDto(op));
                }

                mBlock.setOperations(machineOpDtos);
                mBlock.setTotalOperations(mTotalOps);
                mBlock.setTotalPlannedMinutes(mTotalPlannedMins);

                machineBlocks.add(mBlock);
            }

            wcSched.setMachines(machineBlocks);
            wcSched.setTotalOperations(wcTotalOps);
            wcSched.setCompletedOperations(wcCompletedOps);
            wcSched.setTotalPlannedMinutes(wcTotalPlannedMins);

            wcSchedules.add(wcSched);
        }

        dto.setWorkCenterSchedules(wcSchedules);
        dto.setTotalOperations(totalOps);
        dto.setCompletedOperations(completedOps);
        dto.setInProgressOperations(inProgressOps);
        dto.setTotalPlannedMinutes(totalPlannedMins);
        dto.setTotalCompletedMinutes(totalCompletedMins);
        
        if (totalPlannedMins.compareTo(BigDecimal.ZERO) > 0) {
            dto.setUtilizationPercent(totalCompletedMins.divide(totalPlannedMins, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100)).doubleValue());
        } else {
            dto.setUtilizationPercent(0.0);
        }

        return dto;
    }

    private ProductionScheduleDTO.ScheduledOperation mapToDto(WorkOrderOperation op) {
        ProductionScheduleDTO.ScheduledOperation sOp = new ProductionScheduleDTO.ScheduledOperation();
        sOp.setOperationId(op.getId());
        sOp.setSequence(op.getSequence());
        sOp.setOperationName(op.getOperationName());

        if (op.getWorkOrder() != null) {
            sOp.setWorkOrderId(op.getWorkOrder().getId());
            sOp.setWorkOrderNumber(op.getWorkOrder().getWorkOrderNumber());
            if (op.getWorkOrder().getPriority() != null) {
                sOp.setPriority(op.getWorkOrder().getPriority().name());
            }
            if (op.getWorkOrder().getWorkOrderStatus() != null) {
                sOp.setWorkOrderStatus(op.getWorkOrder().getWorkOrderStatus().name());
            }

            if (op.getWorkOrder().getBom() != null && op.getWorkOrder().getBom().getParentInventoryItem() != null) {
                sOp.setItemCode(op.getWorkOrder().getBom().getParentInventoryItem().getItemCode());
                sOp.setItemName(op.getWorkOrder().getBom().getParentInventoryItem().getName());
            }
        }

        sOp.setPlannedQuantity(op.getPlannedQuantity());
        sOp.setCompletedQuantity(op.getCompletedQuantity());
        sOp.setAvailableInputQuantity(op.getAvailableInputQuantity());
        sOp.setScrappedQuantity(op.getScrappedQuantity());
        sOp.setOperationStatus(op.getStatus() != null ? op.getStatus().name() : null);

        sOp.setPlannedStartDate(op.getPlannedStartDate());
        sOp.setPlannedEndDate(op.getPlannedEndDate());
        sOp.setActualStartDate(op.getActualStartDate());
        sOp.setActualEndDate(op.getActualEndDate());
        sOp.setDurationMinutes(getDuration(op));

        if (op.getWorkCenter() != null) {
            sOp.setWorkCenterCode(op.getWorkCenter().getCenterCode());
        }
        if (op.getAssignedMachine() != null) {
            sOp.setMachineCode(op.getAssignedMachine().getMachineCode());
            sOp.setMachineName(op.getAssignedMachine().getMachineName());
        }

        return sOp;
    }

    /**
     * Calculate actual production minutes from routing operation times.
     * Using date-diff would be incorrect since dates are at start-of-day granularity.
     */
    private BigDecimal getDuration(WorkOrderOperation op) {
        if (op.getRoutingOperation() != null) {
            BigDecimal setupTime = op.getRoutingOperation().getSetupTime() != null
                    ? op.getRoutingOperation().getSetupTime() : BigDecimal.ZERO;
            BigDecimal runTime = op.getRoutingOperation().getRunTime() != null
                    ? op.getRoutingOperation().getRunTime() : BigDecimal.ZERO;
            BigDecimal plannedQty = op.getPlannedQuantity() != null
                    ? op.getPlannedQuantity() : BigDecimal.ZERO;
            return setupTime.add(runTime.multiply(plannedQty)).setScale(2, RoundingMode.HALF_UP);
        }
        // Fallback: estimate from date range (days × 480 min/day)
        if (op.getPlannedStartDate() != null && op.getPlannedEndDate() != null) {
            long ms = op.getPlannedEndDate().getTime() - op.getPlannedStartDate().getTime();
            long days = Math.max(1, ms / (1000 * 60 * 60 * 24) + 1);
            return BigDecimal.valueOf(days * 480L).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}
