package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.LabourType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class WorkOrderLabourEntryRequestDTO {

    private String operatorName;

    private Long laborRoleId;

    private LabourType laborType = LabourType.RUN;

    private Date startTime;

    private Date endTime;

    /** Manual override — if null and startTime+endTime present, computed automatically. */
    private BigDecimal durationMinutes;

    /** Manual override — if null and laborRoleId present, pulled from LaborRole.costPerHour. */
    private BigDecimal costRatePerHour;

    /**
     * Direct total-cost override. Used for piece-rate (RATE_TIMES_QTY) operations where the
     * cost is rate × qty rather than time × hourly rate. When present it takes precedence over
     * the duration × costRatePerHour computation.
     */
    private BigDecimal totalCost;

    private String remarks;
}
