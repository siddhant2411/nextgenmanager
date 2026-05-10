package com.nextgenmanager.nextgenmanager.production.repository.workcenter;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.DowntimeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DowntimeEventRepository extends JpaRepository<DowntimeEvent, Long> {
    
    @Query("SELECT d FROM DowntimeEvent d WHERE d.machine.id = :machineId AND d.endTime IS NULL")
    Optional<DowntimeEvent> findActiveEventByMachine(Long machineId);

    List<DowntimeEvent> findByMachineIdAndStartTimeBetween(Long machineId, java.util.Date start, java.util.Date end);
}
