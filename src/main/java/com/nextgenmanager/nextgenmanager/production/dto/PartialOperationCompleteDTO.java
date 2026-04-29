package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartialOperationCompleteDTO {

    private Long operationId;

    private BigDecimal completedQuantity;

    private BigDecimal scrappedQuantity = BigDecimal.ZERO;

    /** Units pending MRB disposition (repairable / under review). Creates a RejectionEntry. */
    private BigDecimal rejectedQuantity = BigDecimal.ZERO;

    /** Required when scrappedQuantity > 0 */
    private String scrapReasonCode;

    /** Required when rejectedQuantity > 0 */
    private String rejectionReasonCode;

    private String remarks;
}
