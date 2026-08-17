package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.WorkOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * One work order linked to another, flattened to a single row.
 *
 * <p>Deliberately compact: a related work order is a signpost, not a summary. Whoever needs the
 * detail opens it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelatedWorkOrderDTO {

    private int id;

    private String workOrderNumber;

    private WorkOrderStatus status;

    /** How this work order relates to the one being viewed. */
    private Relation relation;

    /** Items it makes — the line item codes, comma separated. Empty when it has no lines. */
    private String items;

    private BigDecimal plannedQuantity;

    private BigDecimal completedQuantity;

    private Date dueDate;

    /** Declaration order is display order: where this came from, then what came out of it. */
    public enum Relation {
        /** The work order this one was raised under. */
        PARENT,
        /** The work order this one was split out of. */
        SPLIT_PARENT,
        /** Raised under the work order being viewed. */
        CHILD,
        /** Exists because quantity was split off the work order being viewed. */
        SPLIT_CHILD
    }
}
