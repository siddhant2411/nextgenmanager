package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {

    @Query("SELECT s FROM StorageLocation s WHERE s.warehouse.id = :warehouseId AND s.deletedDate IS NULL ORDER BY s.code")
    List<StorageLocation> findLiveByWarehouse(@Param("warehouseId") Long warehouseId);

    /** Pickable locations only — staging, inspection and damage bins are excluded by design. */
    @Query("SELECT s FROM StorageLocation s WHERE s.warehouse.id = :warehouseId "
            + "AND s.deletedDate IS NULL AND s.active = true AND s.pickable = true ORDER BY s.code")
    List<StorageLocation> findPickableByWarehouse(@Param("warehouseId") Long warehouseId);

    @Query("SELECT s FROM StorageLocation s WHERE s.id = :id AND s.deletedDate IS NULL")
    Optional<StorageLocation> findLiveById(@Param("id") Long id);

    @Query("SELECT s FROM StorageLocation s WHERE s.warehouse.id = :warehouseId "
            + "AND UPPER(s.code) = UPPER(:code) AND s.deletedDate IS NULL")
    Optional<StorageLocation> findLiveByWarehouseAndCode(@Param("warehouseId") Long warehouseId,
                                                         @Param("code") String code);

    @Query("SELECT COUNT(s) FROM StorageLocation s WHERE s.warehouse.id = :warehouseId AND s.deletedDate IS NULL")
    long countLiveByWarehouse(@Param("warehouseId") Long warehouseId);
}
