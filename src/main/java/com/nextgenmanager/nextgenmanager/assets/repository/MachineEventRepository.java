package com.nextgenmanager.nextgenmanager.assets.repository;

import com.nextgenmanager.nextgenmanager.assets.model.MachineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MachineEventRepository extends JpaRepository<MachineEvent, Long> {
    Optional<MachineEvent> findFirstByMachineIdAndEndTimeIsNullOrderByStartTimeDesc(Long machineId);
}
