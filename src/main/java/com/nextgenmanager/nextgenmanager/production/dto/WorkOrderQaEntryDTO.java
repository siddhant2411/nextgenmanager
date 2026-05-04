package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.bom.enums.QaParameterType;
import com.nextgenmanager.nextgenmanager.production.enums.QaResult;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
public class WorkOrderQaEntryDTO {
    private Long id;
    private Long workOrderOperationId;
    private Long bomQaParameterId;
    private String parameterName;
    private QaParameterType parameterType;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private Boolean critical;
    private List<WorkOrderQaResultDTO> results;
    // PENDING = no results yet, PASS = all results passed, FAIL = any result failed
    private QaResult overallResult;
    private Date creationDate;
}
