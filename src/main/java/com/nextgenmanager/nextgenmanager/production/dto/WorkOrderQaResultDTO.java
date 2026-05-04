package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.QaResult;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
public class WorkOrderQaResultDTO {
    private Long id;
    private Long workOrderQaEntryId;
    private BigDecimal checkedQuantity;
    private BigDecimal actualValue;
    private Boolean passed;
    private QaResult result;
    private String remarks;
    private String checkedBy;
    private Date checkedAt;
    private Date creationDate;
}
