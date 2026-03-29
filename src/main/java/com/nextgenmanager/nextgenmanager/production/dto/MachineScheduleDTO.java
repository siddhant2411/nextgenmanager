package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Operator-facing machine task queue.
 * Shows what work is scheduled on a specific machine.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MachineScheduleDTO {

    private Long machineId;
    private String machineCode;
    private String machineName;
    private String workCenterCode;
    private String workCenterName;

    private List<MachineTask> tasks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MachineTask {
        private Long operationId;
        private String workOrderNumber;
        private String operationName;
        private Integer sequence;

        private BigDecimal plannedQuantity;
        private BigDecimal completedQuantity;
        private BigDecimal availableInputQuantity;

        private String priority;          // URGENT, HIGH, NORMAL, LOW
        private String status;            // PLANNED, READY, IN_PROGRESS, COMPLETED

        private Date plannedStartDate;
        private Date plannedEndDate;
    }
}
