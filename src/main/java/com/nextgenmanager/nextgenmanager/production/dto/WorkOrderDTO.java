package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderSourceType;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import com.nextgenmanager.nextgenmanager.sales.dto.SalesOrderDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderDTO {

    private int id;

    private WorkOrderDTO parentWorkOrder;

    private String workOrderNumber;

    private SalesOrderDto salesOrder;

    private BomDTO bom;

    private RoutingDto routing;

    private InventoryItemDTO inventoryItem;

    private WorkOrderStatus status;

    private WorkOrderPriority priority;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private BigDecimal scrappedQuantity;

    private WorkOrderSourceType sourceType;

    private List<WorkOrderMaterialDTO> materials;

    private List<WorkOrderOperationDTO> operations;

    private String remarks;

    private String workCenter;

    private Date dueDate;

    private Date plannedStartDate;

    private Date plannedEndDate;

    private Date actualStartDate;

    private Date actualEndDate;

    // Scheduling & Estimation
    private BigDecimal estimatedProductionMinutes;

    private BigDecimal estimatedTotalCost;

    private Boolean autoScheduled;

    private boolean allowBackflush;

}
