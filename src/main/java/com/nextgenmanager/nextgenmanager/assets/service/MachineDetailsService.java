package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;

import java.util.List;

public interface MachineDetailsService {

    public MachineDetailsResponseDTO getMachineDetailsById(int id);

    public List<MachineDetailsResponseDTO> getMachineList();

    public MachineDetailsResponseDTO createMachineDetails(MachineDetails machineDetails);

    public MachineDetailsResponseDTO updateMachineDetails(int id,MachineDetails updatedMachineDetails);

    public void deleteMachineDetails(int id);

}
