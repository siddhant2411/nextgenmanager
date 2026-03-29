package com.nextgenmanager.nextgenmanager.production.dto;


import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkCenterMachineDetailsDTO {

    private int id;
    private String centerCode;
    private String centerName;
    private String description;
    private BigDecimal machineCostPerHour;
    private BigDecimal overheadPercentage;
    private BigDecimal availableHoursPerDay;
    private WorkCenter.WorkCenterStatus workCenterStatus;
    private String department;
    private String location;
    private Integer maxLoadPercentage;
    private String supervisor;
    private List<String> availableShifts;

}
