package com.nextgenmanager.nextgenmanager.bom.dto;

import com.nextgenmanager.nextgenmanager.bom.enums.QaParameterType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BomQaParameterRequestDTO {
    private String name;
    private QaParameterType parameterType;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private Boolean critical = false;
}
