package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public interface MachineDetailsService {

    public MachineDetailsResponseDTO getMachineDetailsById(long id);

    public List<MachineDetailsResponseDTO> getMachineList();

    public MachineDetailsResponseDTO createMachineDetails(MachineDetails machineDetails);

    public MachineDetailsResponseDTO updateMachineDetails(long id,MachineDetails updatedMachineDetails);

    public MachineDetailsResponseDTO changeMachineStatus(long id, MachineDetails.MachineStatus newStatus, String reason);
    public MachineDetailsResponseDTO changeMachineStatus(long id, MachineDetails.MachineStatus newStatus, String reason, LocalDateTime changedAt);

    public void deleteMachineDetails(long id);

    public Page<MachineDetailsResponseDTO> filterMachineDetails(FilterRequest filterRequest);
}
