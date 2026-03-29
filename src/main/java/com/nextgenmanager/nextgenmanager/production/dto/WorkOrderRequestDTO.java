package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderSourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderRequestDTO {

    private int id;

    private Integer salesOrderId;

    private Integer parentWorkOrderId;

    private Integer bomId;

    private Long routingId;

    private Integer workCenterId;

    private WorkOrderPriority priority;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private BigDecimal scrappedQuantity;

    private WorkOrderSourceType sourceType;

    private String remarks;

    private Date dueDate;

    private Date plannedStartDate;

    private Date plannedEndDate;

    private Date actualStartDate;

    private Date actualEndDate;

}
