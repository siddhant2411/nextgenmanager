package com.nextgenmanager.nextgenmanager.marketing.enquiry.controller;

import com.nextgenmanager.nextgenmanager.production.dto.WorkOrderProductionDTO;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class WorkOrderListRequest {
    // Getters and Setters
    private int page;
    private int size;
    private String sortBy;
    private String sortDir;
    private WorkOrderProductionDTO filterDTO;

}
