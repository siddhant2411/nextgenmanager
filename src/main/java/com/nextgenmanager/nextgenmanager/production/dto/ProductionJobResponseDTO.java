package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.production.model.ProductionJob;
import lombok.*;

import java.math.BigDecimal;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionJobResponseDTO {
    private int id;
    private String jobName;

    private MachineDetailsResponseDTO machineDetails;
    private BigDecimal defaultSetupTime;
    private BigDecimal defaultRunTimePerUnit;

    private WorkCenterResponseDTO workCenter;

    private String category;
    private ProductionJob.JobRole roleRequired;
    private BigDecimal costPerHour;
    private String description;

}
