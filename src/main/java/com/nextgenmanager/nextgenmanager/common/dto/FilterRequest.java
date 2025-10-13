package com.nextgenmanager.nextgenmanager.common.dto;

import lombok.Data;

import java.util.List;

@Data
public class FilterRequest {
    private int page = 0;
    private int size = 10;
    private String sortBy;
    private String sortDir;
    private List<FilterCriteria> filters;
}

