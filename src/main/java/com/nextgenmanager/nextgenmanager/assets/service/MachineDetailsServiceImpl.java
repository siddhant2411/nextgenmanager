package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.dto.MachineDetailsResponseDTO;
import com.nextgenmanager.nextgenmanager.assets.mapper.MachineDetailsResponseMapper;
import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class MachineDetailsServiceImpl implements MachineDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(MachineDetailsServiceImpl.class);

    @Autowired
    private MachineDetailsRepository machineDetailsRepository;

    @Autowired
    private MachineDetailsResponseMapper machineDetailsResponseMapper;

    @Override
    public MachineDetailsResponseDTO getMachineDetailsById(int id) {
        logger.debug("Fetching MachineDetails for ID: {}", id);
        return machineDetailsResponseMapper.toDTO(machineDetailsRepository.findById(id)
                .filter(machineDetails -> machineDetails.getDeletedDate()==null)
                .orElseThrow(() -> {
                    logger.error("MachineDetails not found for ID: {}", id);
                    return new ResourceNotFoundException("MachineDetails not found for ID: " + id);
                }));
    }

    @Override
    public List<MachineDetailsResponseDTO> getMachineList() {
        logger.debug("Fetching all MachineDetails");
        List<MachineDetailsResponseDTO> dtos = machineDetailsRepository.findAll().stream()
                .filter(m -> m.getDeletedDate() == null)
                .map(machineDetailsResponseMapper::toDTO)
                .collect(Collectors.toList());
        logger.debug("Retrieved {} active MachineDetails records", dtos.size());
        return dtos;
    }

    @Override
    public MachineDetailsResponseDTO createMachineDetails(MachineDetails machineDetails) {
        logger.debug("Creating new MachineDetails: {}", machineDetails);
        MachineDetails savedMachine = machineDetailsRepository.save(machineDetails);
        logger.info("Successfully created MachineDetails with ID: {}", savedMachine.getId());
        return machineDetailsResponseMapper.toDTO(savedMachine);
    }

    @Override
    public MachineDetailsResponseDTO updateMachineDetails(int id, MachineDetails updatedMachineDetails) {
        logger.info("Attempting to update MachineDetails with ID: {}", id);

        MachineDetails existingMachine = machineDetailsRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("MachineDetails not found for update, ID: {}", id);
                    return new ResourceNotFoundException("MachineDetails not found for ID: " + id);
                });
        updatedMachineDetails.setId(id);
        return machineDetailsResponseMapper.toDTO(machineDetailsRepository.save(updatedMachineDetails));
    }

    @Override
    public void deleteMachineDetails(int id) {
        logger.debug("Attempting to soft delete MachineDetails with ID: {}", id);
        MachineDetails machineDetailsToDelete = machineDetailsRepository.findById(id)
                .filter(machineDetails -> machineDetails.getDeletedDate()==null)
                .orElseThrow(() -> {
                    logger.error("MachineDetails not found for ID: {}", id);
                    return new ResourceNotFoundException("MachineDetails not found for ID: " + id);
                });
        machineDetailsToDelete.setDeletedDate(new Date());
        machineDetailsRepository.save(machineDetailsToDelete);
        logger.info("Successfully soft deleted MachineDetails with ID: {}", id);
    }
}

