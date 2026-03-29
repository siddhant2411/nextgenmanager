package com.nextgenmanager.nextgenmanager.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FilterCriteria {
    private String field;
    private String operator;
    private String value;
}
