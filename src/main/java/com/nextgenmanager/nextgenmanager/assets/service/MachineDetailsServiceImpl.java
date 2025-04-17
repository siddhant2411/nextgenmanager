package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import com.nextgenmanager.nextgenmanager.assets.repository.MachineDetailsRepository;
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

    @Override
    public MachineDetails getMachineDetailsById(int id) {
        logger.debug("Fetching MachineDetails for ID: {}", id);
        return machineDetailsRepository.findById(id)
                .filter(machineDetails -> machineDetails.getDeletedDate()==null)
                .orElseThrow(() -> {
                    logger.error("MachineDetails not found for ID: {}", id);
                    return new RuntimeException("MachineDetails not found for ID: " + id);
                });
    }

    @Override
    public List<MachineDetails> getMachineList() {
        logger.debug("Fetching all MachineDetails");
        List<MachineDetails> machineList = machineDetailsRepository.findAll().stream()
                .filter(machine -> machine.getDeletedDate() == null)
                .collect(Collectors.toList());
        logger.debug("Retrieved {} active MachineDetails records", machineList.size());
        return machineList;
    }

    @Override
    public MachineDetails createMachineDetails(MachineDetails machineDetails) {
        logger.debug("Creating new MachineDetails: {}", machineDetails);
        MachineDetails savedMachine = machineDetailsRepository.save(machineDetails);
        logger.info("Successfully created MachineDetails with ID: {}", savedMachine.getId());
        return savedMachine;
    }

    @Override
    public MachineDetails updateMachineDetails(int id, MachineDetails updatedMachineDetails) {
        logger.info("Attempting to update MachineDetails with ID: {}", id);

        MachineDetails existingMachine = machineDetailsRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("MachineDetails not found for update, ID: {}", id);
                    return new RuntimeException("MachineDetails not found for ID: " + id);
                });
        updatedMachineDetails.setId(id);
        return machineDetailsRepository.save(updatedMachineDetails);
    }

    @Override
    public void deleteMachineDetails(int id) {
        logger.debug("Attempting to soft delete MachineDetails with ID: {}", id);
        MachineDetails machineDetails = getMachineDetailsById(id);
        machineDetails.setDeletedDate(new Date());
        machineDetailsRepository.save(machineDetails);
        logger.info("Successfully soft deleted MachineDetails with ID: {}", id);
    }
}

