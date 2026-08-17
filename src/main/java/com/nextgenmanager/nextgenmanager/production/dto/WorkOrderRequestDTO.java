package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderPriority;
import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderSourceType;
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

    private Integer salesOrderId;

    private Integer parentWorkOrderId;

    /**
     * The items to manufacture, one line each. When empty, the flat {@link #bomId} /
     * {@link #routingId} / {@link #plannedQuantity} fields below are treated as a single line,
     * so existing single-item callers keep working unchanged.
     */
    private List<WorkOrderLineRequestDTO> lines;

    private Integer bomId;

    private Long routingId;

    private Integer workCenterId;

    private WorkOrderPriority priority;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private BigDecimal scrappedQuantity;

    private WorkOrderSourceType sourceType;

    /** Free-text reference, used only when sourceType is MANUAL. */
    private String referenceDocument;

    private String remarks;

    private Date dueDate;

    private Date plannedStartDate;

    private Date plannedEndDate;

    private Date actualStartDate;

    private Date actualEndDate;

    private boolean allowBackflush;

}
