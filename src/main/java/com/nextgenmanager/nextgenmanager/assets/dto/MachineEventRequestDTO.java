package com.nextgenmanager.nextgenmanager.assets.dto;

import com.nextgenmanager.nextgenmanager.assets.model.MachineEvent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class MachineEventRequestDTO {

    @NotNull
    private Long machineId;

    @NotNull
    private MachineEvent.EventType eventType;

    @NotNull
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @NotNull
    private MachineEvent.Source source;
}
