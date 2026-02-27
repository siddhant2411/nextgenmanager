package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.OperationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderOperationDTO {

    private Long id;

    private RoutingOperationDto routingOperation;

    private Integer sequence;

    private String operationName;

    private WorkCenterResponseDTO workCenter;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private BigDecimal scrappedQuantity;

    private Date plannedStartDate;
    private Date plannedEndDate;

    private Date actualStartDate;
    private Date actualEndDate;

    private OperationStatus status;

    private Boolean isMilestone;

    private Boolean allowOverCompletion;
}
