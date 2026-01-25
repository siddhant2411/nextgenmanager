package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class WorkOrderJobListsResponseDTO {

    private int id;
    private Integer operationNumber;
    private ProductionJobResponseDTO productionJob;
    private WorkCenterResponseDTO workCenter;
    private BigDecimal setupTime;
    private BigDecimal runTimePerUnit;
    private BigDecimal labourCost;
    private BigDecimal overheadCost;
    private String operationDescription;
    private Boolean isParallelOperation;
    private String toolingRequirements;
    private Integer skillLevelRequired;

}
