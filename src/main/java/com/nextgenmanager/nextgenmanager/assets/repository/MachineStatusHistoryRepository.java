package com.nextgenmanager.nextgenmanager.assets.repository;

import com.nextgenmanager.nextgenmanager.assets.model.MachineStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MachineStatusHistoryRepository extends JpaRepository<MachineStatusHistory, Long> {
    Page<MachineStatusHistory> findByMachineId(Long machineId, Pageable pageable);

    Optional<MachineStatusHistory>
    findTopByMachineIdOrderByChangedAtDesc(long id);

    @Query("""
            select msh
            from MachineStatusHistory msh
            where msh.machine.id = :machineId
              and (
                    msh.changedAt > :changedAt
                    or (msh.changedAt = :changedAt and msh.id > :historyId)
                  )
            order by msh.changedAt asc, msh.id asc
            """)
    List<MachineStatusHistory> findNextStatusEntries(@Param("machineId") Long machineId,
                                                     @Param("changedAt") LocalDateTime changedAt,
                                                     @Param("historyId") Long historyId,
                                                     Pageable pageable);
}
