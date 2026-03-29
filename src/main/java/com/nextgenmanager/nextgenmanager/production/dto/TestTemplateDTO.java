package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.InspectionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestTemplateDTO {

    private Long id;
    private Integer inventoryItemId;
    private String testName;
    private InspectionType inspectionType;
    private Integer sampleSize;
    private Boolean isMandatory;
    private Integer sequence;
    private String acceptanceCriteria;
    private String unitOfMeasure;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private boolean active;
}
