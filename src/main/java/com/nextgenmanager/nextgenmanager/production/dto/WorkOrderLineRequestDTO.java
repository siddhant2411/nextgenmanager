package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * One finished item to manufacture within a work order.
 *
 * <p>A work order request carries a list of these. The routing is optional — when omitted it is
 * resolved from the line's BOM, which is how the single-line form has always behaved.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderLineRequestDTO {

    private Integer bomId;

    /** Optional; resolved from the BOM when not supplied. */
    private Long routingId;

    private BigDecimal plannedQuantity;

    /** Optional per-line override of the work order's due date. */
    private Date dueDate;

    /** Optional link to the sales-order line this line satisfies. */
    private Long salesOrderItemId;
}
