package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.model.MachineEvent;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineEventRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
public class MachineEventServiceImpl implements MachineEventService {

    @Autowired
    private MachineEventRepository machineEventRepository;

    @Autowired
    private MachineDetailsRepository machineDetailsRepository;

    @Autowired
    private MachineDetailsService machineDetailsService;



    @Override
    @Transactional
    public MachineEvent createEvent(Long machineId,
                                    MachineEvent.EventType eventType,
                                    LocalDateTime startTime,
                                    LocalDateTime endTime,
                                    MachineEvent.Source source) {
        if (machineId == null) {
            throw new IllegalArgumentException("Machine ID is required");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("Event type is required");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Start time is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("Source is required");
        }

        MachineDetails machine = machineDetailsRepository.findByIdAndDeletedDateIsNull(machineId)
                .orElseThrow(() -> new ResourceNotFoundException("MachineDetails not found for ID: " + machineId));

        MachineEvent machineEvent = new MachineEvent();
        machineEvent.setMachine(machine);
        machineEvent.setEventType(eventType);
        machineEvent.setStartTime(startTime);
        machineEvent.setEndTime(endTime);
        machineEvent.setSource(source);
        return save(machineEvent);
    }

    @Override
    @Transactional
    public MachineEvent save(MachineEvent machineEvent) {

        if (machineEvent.getMachine() == null || machineEvent.getMachine().getId() == null) {
            throw new IllegalArgumentException("Machine is required");
        }

        if (machineEvent.getStartTime() == null) {
            throw new IllegalArgumentException("Start time is required");
        }

        if (machineEvent.getEndTime() != null &&
                machineEvent.getEndTime().isBefore(machineEvent.getStartTime())) {
            throw new IllegalArgumentException("End time cannot be before start time");
        }

        Long machineId = machineEvent.getMachine().getId();

        Optional<MachineEvent> openEventOpt =
                machineEventRepository
                        .findFirstByMachineIdAndEndTimeIsNullOrderByStartTimeDesc(machineId);

        if (openEventOpt.isPresent()) {

            MachineEvent openEvent = openEventOpt.get();

            // Ignore self-update
            if (machineEvent.getId() == null ||
                    !Objects.equals(openEvent.getId(), machineEvent.getId())) {

                if (machineEvent.getStartTime().isBefore(openEvent.getStartTime())) {
                    throw new IllegalArgumentException(
                            "New event start time cannot be before current active event start time");
                }

                //  Close previous event BEFORE inserting new one
                openEvent.setEndTime(machineEvent.getStartTime());
                machineEventRepository.saveAndFlush(openEvent);
            }
        }

        // Now safe to insert new event (can have null endTime)
        MachineEvent savedEvent = machineEventRepository.save(machineEvent);

        MachineDetails.MachineStatus derivedStatus =
                mapEventTypeToMachineStatus(savedEvent.getEventType());

        machineDetailsService.changeMachineStatus(
                machineId,
                derivedStatus,
                "Auto-updated from machine event: " + savedEvent.getEventType(),
                savedEvent.getStartTime()
        );

        return savedEvent;
    }

    private MachineDetails.MachineStatus mapEventTypeToMachineStatus(MachineEvent.EventType eventType) {
        return switch (eventType) {
            case BREAKDOWN -> MachineDetails.MachineStatus.BREAKDOWN;
            case MAINTENANCE -> MachineDetails.MachineStatus.UNDER_MAINTENANCE;
            case RUNNING, IDLE -> MachineDetails.MachineStatus.ACTIVE;
        };
    }
}
