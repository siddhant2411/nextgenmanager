package com.nextgenmanager.nextgenmanager.production.repository.workcenter;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.DowntimeReasonCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DowntimeReasonCodeRepository extends JpaRepository<DowntimeReasonCode, Long> {
    List<DowntimeReasonCode> findByIsActiveTrue();
    Optional<DowntimeReasonCode> findByCode(String code);
}
