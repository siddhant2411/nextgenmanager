package com.nextgenmanager.nextgenmanager.sales.dto;

import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderStatus;
import lombok.Data;

@Data
public class StatusUpdateRequest {
    private SalesOrderStatus status;
}
