package com.nextgenmanager.nextgenmanager.packaging.repository;

import com.nextgenmanager.nextgenmanager.packaging.model.PackageBox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackageBoxRepository extends JpaRepository<PackageBox, Long> {

    @Query("SELECT b FROM PackageBox b WHERE b.id = :id AND b.deletedDate IS NULL")
    Optional<PackageBox> findLiveById(@Param("id") Long id);

    @Query("SELECT b FROM PackageBox b WHERE b.packingSlip.id = :slipId AND b.deletedDate IS NULL "
            + "ORDER BY b.boxNumber")
    List<PackageBox> findLiveByPackingSlip(@Param("slipId") Long slipId);

    @Query("SELECT COALESCE(MAX(b.boxNumber), 0) FROM PackageBox b WHERE b.packingSlip.id = :slipId "
            + "AND b.deletedDate IS NULL")
    Integer maxBoxNumber(@Param("slipId") Long slipId);
}
