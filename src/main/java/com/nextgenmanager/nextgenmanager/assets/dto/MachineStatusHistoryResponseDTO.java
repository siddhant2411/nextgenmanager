package com.nextgenmanager.nextgenmanager.assets.dto;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineStatusHistory;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class MachineStatusHistoryResponseDTO {
    private Long id;
    private Long machineId;
    private MachineDetails.MachineStatus oldStatus;
    private MachineDetails.MachineStatus newStatus;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String changedBy;
    private String reason;
    private MachineStatusHistory.Source source;
    private LocalDateTime createdAt;
}
