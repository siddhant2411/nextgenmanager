package com.nextgenmanager.nextgenmanager.accounting.gst.filing.repository;

import com.nextgenmanager.nextgenmanager.accounting.gst.filing.model.GstReturn;
import com.nextgenmanager.nextgenmanager.accounting.gst.filing.model.GstReturnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GstReturnRepository extends JpaRepository<GstReturn, Long> {

    Optional<GstReturn> findByIdAndDeletedDateIsNull(Long id);

    Optional<GstReturn> findByPeriodIdAndReturnTypeAndDeletedDateIsNull(Long periodId, GstReturnType returnType);

    @Query("""
        SELECT r FROM GstReturn r
        WHERE r.financialYear.id = :fyId
          AND r.deletedDate IS NULL
        ORDER BY r.period.periodNumber ASC, r.returnType ASC
        """)
    List<GstReturn> findFiledByFinancialYear(@Param("fyId") Long fyId);
}
