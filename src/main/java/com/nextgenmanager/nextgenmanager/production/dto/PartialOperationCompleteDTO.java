package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartialOperationCompleteDTO {

    /**
     * WorkOrderOperation ID
     */
    private Long operationId;

    /**
     * Quantity completed in this partial completion
     */
    private BigDecimal completedQuantity;

    /**
     * Scrapped quantity (optional)
     */
    private BigDecimal scrappedQuantity = BigDecimal.ZERO;

    /**
     * Remarks about the partial completion
     */
    private String remarks;
}
