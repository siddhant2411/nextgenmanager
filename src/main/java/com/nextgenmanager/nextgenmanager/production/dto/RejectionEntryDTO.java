package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.DispositionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectionEntryDTO {

    private Long id;
    private int workOrderId;
    private String workOrderNumber;
    private Long operationId;
    private String operationName;
    private Integer operationSequence;
    private BigDecimal rejectedQuantity;
    private DispositionStatus dispositionStatus;
    private String dispositionReason;
    private Integer childWorkOrderId;
    private String childWorkOrderNumber;
    private Date createdAt;
    private String createdBy;
    private Date disposedAt;
    private String disposedBy;
}
