package com.nextgenmanager.nextgenmanager.assets.dto;

import com.nextgenmanager.nextgenmanager.assets.model.MachineEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class MachineEventResponseDTO {
    private Long id;
    private Long machineId;
    private MachineEvent.EventType eventType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private MachineEvent.Source source;
    private LocalDateTime createdAt;
}
