package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.ChallanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobWorkChallanDTO {

    private Long id;
    private String challanNumber;

    private int vendorId;
    private String vendorName;
    private String vendorGstNumber;

    private Long workOrderId;
    private String workOrderNumber;

    private Long workOrderOperationId;
    private String workOrderOperationName;

    private ChallanStatus status;

    private Date dispatchDate;
    private Date expectedReturnDate;
    private Date actualReturnDate;

    /** Days remaining before the 180-day statutory deadline. Negative = overdue. */
    private Long daysRemainingForReturn;

    private BigDecimal agreedRatePerUnit;
    private String dispatchDetails;
    private String remarks;

    private List<LineDTO> lines;

    private Date creationDate;
    private Date updatedDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDTO {
        private Long id;
        private Integer inventoryItemId;
        private String itemCode;
        private String itemName;
        private String description;
        private String hsnCode;
        private BigDecimal quantityDispatched;
        private BigDecimal quantityReceived;
        private BigDecimal quantityRejected;
        /** Pending = dispatched - received - rejected */
        private BigDecimal quantityPending;
        private String uom;
        private BigDecimal valuePerUnit;
        private String remarks;
        private Date lastReceiptDate;
    }
}
