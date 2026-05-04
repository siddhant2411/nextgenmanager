package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.dto.DowntimeEventRequestDTO;
import com.nextgenmanager.nextgenmanager.production.dto.DowntimeEventResponseDTO;
import com.nextgenmanager.nextgenmanager.production.dto.DowntimeReasonCodeDTO;
import com.nextgenmanager.nextgenmanager.production.model.DowntimeEvent;

import java.util.List;

public interface DowntimeService {
    DowntimeEventResponseDTO startDowntime(DowntimeEventRequestDTO request);
    DowntimeEventResponseDTO stopDowntime(Long eventId);
    java.util.Optional<DowntimeEventResponseDTO> getActiveEventByMachine(Long machineId);
    List<DowntimeReasonCodeDTO> getAllReasonCodes();
    DowntimeReasonCodeDTO createReasonCode(DowntimeReasonCodeDTO dto);
}
