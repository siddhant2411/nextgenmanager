package com.nextgenmanager.nextgenmanager.production.dto;

import com.nextgenmanager.nextgenmanager.production.enums.DowntimeCategory;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class DowntimeEventResponseDTO {
    private Long id;
    private Long machineId;
    private String machineName;
    private Long reasonCodeId;
    private String reasonCode;
    private String reasonDescription;
    private DowntimeCategory category;
    private Date startTime;
    private Date endTime;
    private Integer durationMinutes;
    private String remarks;
    private String reportedBy;
}
