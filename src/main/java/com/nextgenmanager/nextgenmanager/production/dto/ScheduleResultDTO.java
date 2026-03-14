package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Response DTO showing the scheduling outcome for a Work Order.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResultDTO {

    private int workOrderId;
    private String workOrderNumber;
    private Date plannedStartDate;
    private Date plannedEndDate;

    private BigDecimal estimatedProductionMinutes;
    private BigDecimal estimatedTotalCost;

    private List<OperationSchedule> operationSchedules;
    private List<String> warnings;  // e.g., "Due date will be missed by 2 days"

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationSchedule {
        private Long operationId;
        private int sequence;
        private String operationName;
        private String workCenterCode;
        private String machineCode;
        private Date plannedStartDate;
        private Date plannedEndDate;
        private BigDecimal durationMinutes;
        /** Parallel path label for Gantt grouping (e.g. "PATH_A"). Null for sequential ops. */
        private String parallelPath;
    }
}
