package com.nextgenmanager.nextgenmanager.quality.repository;

import com.nextgenmanager.nextgenmanager.quality.model.NcrStatus;
import com.nextgenmanager.nextgenmanager.quality.model.NonConformanceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NonConformanceReportRepository extends JpaRepository<NonConformanceReport, Long> {

    @Query("SELECT n FROM NonConformanceReport n WHERE n.id = :id AND n.deletedDate IS NULL")
    Optional<NonConformanceReport> findLiveById(@Param("id") Long id);

    @Query("SELECT n FROM NonConformanceReport n WHERE n.deletedDate IS NULL "
            + "ORDER BY n.creationDate DESC")
    List<NonConformanceReport> findAllLive();

    @Query("SELECT n FROM NonConformanceReport n WHERE n.status = :status AND n.deletedDate IS NULL "
            + "ORDER BY n.creationDate DESC")
    List<NonConformanceReport> findLiveByStatus(@Param("status") NcrStatus status);

    @Query("SELECT n FROM NonConformanceReport n WHERE n.inspectionLot.id = :lotId "
            + "AND n.deletedDate IS NULL ORDER BY n.creationDate")
    List<NonConformanceReport> findLiveByLot(@Param("lotId") Long lotId);
}
