package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.bom.dto.BomConnectDTO;
import com.nextgenmanager.nextgenmanager.production.helper.RoutingStatus;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrderProductionTemplateResponseDTO {

    private int id;
    private BomConnectDTO bom;
    private Integer routingVersionNumber;
    private String routingVersion;
    private RoutingStatus routingStatus;
    private List<WorkOrderJobListsResponseDTO> workOrderJobLists;
    private BigDecimal totalSetupTime;
    private BigDecimal totalRunTime;
    private BigDecimal estimatedHours;
    private BigDecimal estimatedCostOfLabour;
    private BigDecimal estimatedCostOfBom;
    private BigDecimal overheadCostPercentage;
    private BigDecimal overheadCostValue;
    private BigDecimal totalCostOfWorkOrder;
    private WorkCenterResponseDTO defaultWorkCenter;
    private String details;
    private Boolean isSequenceValidated;
    private WorkOrderProductionTemplate.CostingMethod costingMethod;
    private Date effectiveFrom;
    private Date effectiveTo;
    private String changeReason;
    private String changedBy;
    private Integer versionNumber;
    private Boolean isActiveVersion;


}
