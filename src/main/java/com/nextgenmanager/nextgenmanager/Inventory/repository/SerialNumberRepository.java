package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.SerialNumber;
import com.nextgenmanager.nextgenmanager.Inventory.model.SerialStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SerialNumberRepository extends JpaRepository<SerialNumber, Long> {

    Optional<SerialNumber> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);

    List<SerialNumber> findByBatch_Id(Long batchId);

    @Query("SELECT s FROM SerialNumber s WHERE s.inventoryItem.inventoryItemId = :itemId " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:search IS NULL OR LOWER(s.serialNumber) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))) " +
           "ORDER BY s.createdDate DESC")
    Page<SerialNumber> searchByItem(@Param("itemId") int itemId,
                                    @Param("status") SerialStatus status,
                                    @Param("search") String search,
                                    Pageable pageable);

    @Query("SELECT s FROM SerialNumber s WHERE s.sourceDocNo = :docNo ORDER BY s.createdDate ASC")
    List<SerialNumber> findBySourceDocNo(@Param("docNo") String docNo);

    /** All serials dispatched / consumed by a specific document (DN number, SO number, WO number). */
    @Query("SELECT s FROM SerialNumber s WHERE s.consumedByDocNo = :docNo ORDER BY s.consumedDate ASC")
    List<SerialNumber> findByConsumedByDocNo(@Param("docNo") String docNo);
}
