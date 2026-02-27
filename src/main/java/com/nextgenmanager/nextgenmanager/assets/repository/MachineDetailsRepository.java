package com.nextgenmanager.nextgenmanager.assets.repository;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MachineDetailsRepository  extends JpaRepository<MachineDetails,Long>, JpaSpecificationExecutor<MachineDetails> {
    Optional<MachineDetails> findByIdAndDeletedDateIsNull(Long id);
    List<MachineDetails> findByDeletedDateIsNull();
    Optional<MachineDetails> findByMachineCodeAndDeletedDateIsNull(String machineCode);
}
