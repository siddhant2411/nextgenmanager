package com.nextgenmanager.nextgenmanager.production.dto;

import lombok.Data;

@Data
public class DowntimeEventRequestDTO {
    private Long machineId;
    private Long shiftId;
    private Long reasonCodeId;
    private Long workOrderOperationId;
    private String remarks;
}
