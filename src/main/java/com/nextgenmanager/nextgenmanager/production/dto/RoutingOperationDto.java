package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Builder
@AllArgsConstructor
@Getter
@Setter
public class RoutingOperationDto {

    private Long id;
    private Integer sequenceNumber;
    private String name;

    private ProductionJobResponseDTO productionJob;
    private WorkCenterResponseDTO workCenter;

    private BigDecimal setupTime;
    private BigDecimal runTime;

    private Boolean inspection;
    private String notes;

}
