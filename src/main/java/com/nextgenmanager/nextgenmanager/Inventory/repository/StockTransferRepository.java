package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.StockTransfer;
import com.nextgenmanager.nextgenmanager.Inventory.model.StockTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    @Query("SELECT t FROM StockTransfer t WHERE t.id = :id AND t.deletedDate IS NULL")
    Optional<StockTransfer> findLiveById(@Param("id") Long id);

    @Query("SELECT t FROM StockTransfer t WHERE t.deletedDate IS NULL ORDER BY t.creationDate DESC")
    List<StockTransfer> findAllLive();

    @Query("SELECT t FROM StockTransfer t WHERE t.status = :status AND t.deletedDate IS NULL "
            + "ORDER BY t.creationDate DESC")
    List<StockTransfer> findLiveByStatus(@Param("status") StockTransferStatus status);

    @Query("SELECT t FROM StockTransfer t WHERE t.deletedDate IS NULL "
            + "AND (t.fromWarehouse.id = :warehouseId OR t.toWarehouse.id = :warehouseId) "
            + "ORDER BY t.creationDate DESC")
    List<StockTransfer> findLiveByWarehouse(@Param("warehouseId") Long warehouseId);
}
