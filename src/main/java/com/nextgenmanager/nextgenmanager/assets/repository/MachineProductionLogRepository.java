package com.nextgenmanager.nextgenmanager.assets.repository;

import com.nextgenmanager.nextgenmanager.assets.model.MachineProductionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface MachineProductionLogRepository extends JpaRepository<MachineProductionLog, Long> {
    Page<MachineProductionLog> findByMachineId(Long machineId, Pageable pageable);
    Optional<MachineProductionLog> findByMachineIdAndProductionDateAndShiftId(Long machineId, LocalDate productionDate, Long shiftId);
    Optional<MachineProductionLog> findByMachineIdAndProductionDateAndShiftIdIsNull(Long machineId, LocalDate productionDate);
}
