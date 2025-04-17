package com.nextgenmanager.nextgenmanager.assets.service;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;

import java.util.List;

public interface MachineDetailsService {

    public MachineDetails getMachineDetailsById(int id);

    public List<MachineDetails> getMachineList();

    public MachineDetails createMachineDetails(MachineDetails machineDetails);

    public MachineDetails updateMachineDetails(int id,MachineDetails updatedMachineDetails);

    public void deleteMachineDetails(int id);

}
