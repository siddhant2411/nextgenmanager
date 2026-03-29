package com.nextgenmanager.nextgenmanager.production.dto;


import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderProductionDTO {

    private int id;

    private String salesOrderNumber;

    private String bomName;

    private WorkOrderStatus status;

    private Date dueDate;

    private BigDecimal actualCost;

    private Date creationDate;




}
