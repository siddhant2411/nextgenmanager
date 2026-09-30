package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.Warehouse;
import com.nextgenmanager.nextgenmanager.Inventory.model.WarehouseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    @Query("SELECT w FROM Warehouse w WHERE w.deletedDate IS NULL ORDER BY w.code")
    List<Warehouse> findAllLive();

    @Query("SELECT w FROM Warehouse w WHERE w.deletedDate IS NULL AND w.active = true ORDER BY w.code")
    List<Warehouse> findAllActive();

    @Query("SELECT w FROM Warehouse w WHERE w.id = :id AND w.deletedDate IS NULL")
    Optional<Warehouse> findLiveById(@Param("id") Long id);

    @Query("SELECT w FROM Warehouse w WHERE UPPER(w.code) = UPPER(:code) AND w.deletedDate IS NULL")
    Optional<Warehouse> findLiveByCode(@Param("code") String code);

    @Query("SELECT w FROM Warehouse w WHERE w.warehouseType = :type AND w.deletedDate IS NULL ORDER BY w.code")
    List<Warehouse> findLiveByType(@Param("type") WarehouseType type);

    /**
     * Written as JPQL rather than a derived name because the field is {@code isDefault}: Lombok
     * generates {@code isDefault()}/{@code setDefault()} for it, so bean introspection sees the
     * property as "default" and a derived query name would be resolved inconsistently.
     */
    @Query("SELECT w FROM Warehouse w WHERE w.isDefault = true AND w.deletedDate IS NULL")
    Optional<Warehouse> findDefault();

    @Query("SELECT w FROM Warehouse w WHERE w.isDefault = true AND w.deletedDate IS NULL AND w.id <> :excludeId")
    List<Warehouse> findOtherDefaults(@Param("excludeId") Long excludeId);
}
