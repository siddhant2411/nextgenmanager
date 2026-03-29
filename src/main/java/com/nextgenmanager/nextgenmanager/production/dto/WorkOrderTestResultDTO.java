package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.InspectionType;
import com.nextgenmanager.nextgenmanager.production.enums.TestResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderTestResultDTO {

    private Long id;
    private int workOrderId;
    private Long testTemplateId;

    // Frozen template snapshot
    private String testName;
    private InspectionType inspectionType;
    private Integer sampleSize;
    private Boolean isMandatory;
    private Integer sequence;
    private String acceptanceCriteria;
    private String unitOfMeasure;
    private BigDecimal minValue;
    private BigDecimal maxValue;

    // Actual results
    private BigDecimal resultValue;
    private Integer testedQuantity;
    private Integer passedQuantity;
    private Integer failedQuantity;
    private TestResult result;
    private String testedBy;
    private Date testDate;
    private String remarks;
}
