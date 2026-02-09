package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderSourceType;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.production.model.*;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderRequestDTO {

    private int id;

    private SalesOrder salesOrder;

    private WorkOrder parentWorkOrder;

    private Bom bom;

    private Routing routing;

    private WorkOrderStatus status;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private BigDecimal scrappedQuantity;

    private WorkOrderSourceType sourceType;

    private List<WorkOrderMaterial> materials;

    private List<WorkOrderOperation> operations;

    private String remarks;

    private WorkCenter workCenter;

    private Date dueDate;

    private Date plannedStartDate;

    private Date plannedEndDate;

    private Date actualStartDate;

    private Date actualEndDate;


}
