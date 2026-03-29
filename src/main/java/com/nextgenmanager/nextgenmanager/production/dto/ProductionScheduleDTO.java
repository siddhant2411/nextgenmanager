package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Production schedule views: Work Center-wise, combined (all WCs), and machine-wise.
 * Used by plant managers to see the full production load across the shop floor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductionScheduleDTO {

    private Date fromDate;
    private Date toDate;
    private List<WorkCenterSchedule> workCenterSchedules;

    // ── Summary stats ──
    private int totalOperations;
    private int completedOperations;
    private int inProgressOperations;
    private BigDecimal totalPlannedMinutes;
    private BigDecimal totalCompletedMinutes;
    private double utilizationPercent;  // completedMinutes / plannedMinutes × 100

    /**
     * Work Center level grouping
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkCenterSchedule {
        private Long workCenterId;
        private String workCenterCode;
        private String workCenterName;

        private List<MachineScheduleBlock> machines;
        private List<ScheduledOperation> operations;  // ops directly on WC (no machine assigned)

        // WC-level summary
        private int totalOperations;
        private int completedOperations;
        private BigDecimal totalPlannedMinutes;
    }

    /**
     * Machine-level grouping within a Work Center
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MachineScheduleBlock {
        private Long machineId;
        private String machineCode;
        private String machineName;
        private String machineStatus;   // ACTIVE, UNDER_MAINTENANCE, etc.

        private List<ScheduledOperation> operations;

        // Machine-level summary
        private int totalOperations;
        private BigDecimal totalPlannedMinutes;
    }

    /**
     * Individual scheduled operation (common structure for all views)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduledOperation {
        private Long operationId;
        private Integer sequence;
        private String operationName;

        // Work Order info
        private int workOrderId;
        private String workOrderNumber;
        private String priority;
        private String workOrderStatus;
        private String itemCode;        // finished good being manufactured
        private String itemName;

        // Quantities
        private BigDecimal plannedQuantity;
        private BigDecimal completedQuantity;
        private BigDecimal availableInputQuantity;
        private BigDecimal scrappedQuantity;

        // Schedule
        private String operationStatus;
        private Date plannedStartDate;
        private Date plannedEndDate;
        private Date actualStartDate;
        private Date actualEndDate;
        private BigDecimal durationMinutes;

        // Assigned resources
        private String workCenterCode;
        private String machineCode;
        private String machineName;
    }
}
