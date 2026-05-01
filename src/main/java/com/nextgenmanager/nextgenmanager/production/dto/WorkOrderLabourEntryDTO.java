package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.LabourType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderLabourEntryDTO {

    private Long id;

    private Long workOrderOperationId;

    private String operatorName;

    private LaborRoleResponseDTO laborRole;

    private LabourType laborType;

    private Date startTime;

    private Date endTime;

    private BigDecimal durationMinutes;

    private BigDecimal costRatePerHour;

    private BigDecimal totalCost;

    private String remarks;

    private Date creationDate;
}
