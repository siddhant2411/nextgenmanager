package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkCenterRepository extends JpaRepository<WorkCenter, Integer>, JpaSpecificationExecutor<WorkCenter> {

    Optional<WorkCenter> findByCenterCode(String centerCode);

    boolean existsByCenterCode(String centerCode);

    @Query("SELECT w FROM WorkCenter w WHERE " +
            "LOWER(w.centerName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(w.centerCode) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<WorkCenter> search(@Param("query") String query);

    List<WorkCenter> findByWorkCenterStatus(WorkCenter.WorkCenterStatus status);

    @Query("SELECT w FROM WorkCenter w WHERE w.deletedDate IS NULL")
    List<WorkCenter> findAllActive();
}