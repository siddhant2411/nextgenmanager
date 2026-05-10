package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.BatchNumber;
import com.nextgenmanager.nextgenmanager.Inventory.model.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchNumberRepository extends JpaRepository<BatchNumber, Long> {

    Optional<BatchNumber> findByBatchNumber(String batchNumber);

    List<BatchNumber> findByInventoryItem_InventoryItemIdAndStatus(int itemId, BatchStatus status);

    @Query("SELECT b FROM BatchNumber b WHERE b.inventoryItem.inventoryItemId = :itemId " +
           "AND (:status IS NULL OR b.status = :status) " +
           "ORDER BY b.createdDate DESC")
    Page<BatchNumber> findByItemIdAndStatus(@Param("itemId") int itemId,
                                            @Param("status") BatchStatus status,
                                            Pageable pageable);

    @Query("SELECT b FROM BatchNumber b WHERE b.expiryDate <= :date AND b.status = 'ACTIVE'")
    List<BatchNumber> findExpiringOnOrBefore(@Param("date") LocalDate date);

    @Query("SELECT b FROM BatchNumber b WHERE b.sourceDocNo = :docNo")
    List<BatchNumber> findBySourceDocNo(@Param("docNo") String docNo);
}
