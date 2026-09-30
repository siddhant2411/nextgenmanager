package com.nextgenmanager.nextgenmanager.quality.repository;

import com.nextgenmanager.nextgenmanager.quality.model.InspectionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InspectionResultRepository extends JpaRepository<InspectionResult, Long> {

    @Query("SELECT r FROM InspectionResult r WHERE r.inspectionLot.id = :lotId ORDER BY r.id")
    List<InspectionResult> findByLot(@Param("lotId") Long lotId);
}
