package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.model.MachineEvent;

import java.time.LocalDateTime;

public interface MachineEventService {
    MachineEvent createEvent(Long machineId,
                             MachineEvent.EventType eventType,
                             LocalDateTime startTime,
                             LocalDateTime endTime,
                             MachineEvent.Source source);

    MachineEvent save(MachineEvent machineEvent);
}
