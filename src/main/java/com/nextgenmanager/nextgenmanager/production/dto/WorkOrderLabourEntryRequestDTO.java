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

    private String remarks;
}
