package com.nextgenmanager.nextgenmanager.bom.dto;

import com.nextgenmanager.nextgenmanager.bom.enums.QaParameterType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
public class BomQaParameterDTO {
    private Long id;
    private Long routingOperationId;
    private String name;
    private QaParameterType parameterType;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private Boolean critical;
    private Date creationDate;
}
