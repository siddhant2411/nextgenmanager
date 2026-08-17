package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/** One finished item within a work order, as returned to the client. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderLineDTO {

    private Long id;

    private Integer lineNumber;

    private InventoryItemDTO inventoryItem;

    private Integer bomId;

    private String bomName;

    private Long routingId;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private BigDecimal scrappedQuantity;

    private WorkOrderStatus status;

    private Long salesOrderItemId;

    private Date dueDate;

    private BigDecimal estimatedProductionMinutes;

    private BigDecimal estimatedTotalCost;

    // Yield, computed per line — a work-order-wide figure across unlike items is meaningless.
    private BigDecimal firstPassYield;

    private BigDecimal scrapRate;

    private BigDecimal overallYield;
}
