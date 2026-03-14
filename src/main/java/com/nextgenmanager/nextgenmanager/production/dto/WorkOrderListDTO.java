package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderSourceType;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderListDTO {

    private int id;

    private String workOrderNumber;

    private String parentWorkOrderNumber;

    private String salesOrderNumber;

    private String bomName;

    private WorkOrderStatus status;

    private WorkOrderPriority priority;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private WorkOrderSourceType sourceType;

    private String workCenter;

    private Date dueDate;

    private Date plannedStartDate;

    private Date plannedEndDate;

    private Date actualStartDate;

    private Date actualEndDate;

    private Boolean autoScheduled;

}
