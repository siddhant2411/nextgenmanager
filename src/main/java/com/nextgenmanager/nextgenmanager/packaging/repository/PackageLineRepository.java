package com.nextgenmanager.nextgenmanager.packaging.repository;

import com.nextgenmanager.nextgenmanager.packaging.model.PackageLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface PackageLineRepository extends JpaRepository<PackageLine, Long> {

    /**
     * How much of a pick line has already gone into a box on its (live) packing slip. Boxes on a
     * cancelled slip do not count — their allocation was released.
     */
    @Query("SELECT COALESCE(SUM(pl.quantity), 0) FROM PackageLine pl "
            + "WHERE pl.pickListLine.id = :pickListLineId "
            + "AND pl.packageBox.deletedDate IS NULL "
            + "AND pl.packageBox.packingSlip.deletedDate IS NULL "
            + "AND pl.packageBox.packingSlip.status <> com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus.CANCELLED")
    BigDecimal sumAlreadyPackaged(@Param("pickListLineId") Long pickListLineId);
}
