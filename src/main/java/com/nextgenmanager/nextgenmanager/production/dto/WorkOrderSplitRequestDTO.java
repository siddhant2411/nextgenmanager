package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Request to split quantity off a work order into a new one.
 *
 * <p>Quantity is always stated per line. A work order's header quantity is the sum across its
 * lines, and a bare "split 4 off" cannot say which item those 4 belong to — on a two-item order
 * it is not even well formed. Lines the caller omits stay entirely with the source.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderSplitRequestDTO {

    /** Per-line quantities to move. At least one entry, each with a quantity above zero. */
    private List<LineSplit> lines;

    /** Due date for the new work order. Falls back to the source's when null. */
    private Date dueDate;

    /** Free text recorded on the new work order and in both audit trails. */
    private String remarks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineSplit {

        /** Id of the source {@link com.nextgenmanager.nextgenmanager.production.model.WorkOrderLine}. */
        private Long lineId;

        /** How much of that line moves to the new work order. */
        private BigDecimal quantity;
    }
}
