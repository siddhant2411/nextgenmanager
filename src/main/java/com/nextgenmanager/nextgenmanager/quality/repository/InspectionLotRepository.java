package com.nextgenmanager.nextgenmanager.quality.repository;

import com.nextgenmanager.nextgenmanager.quality.model.InspectionLot;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionLotStatus;
import com.nextgenmanager.nextgenmanager.quality.model.InspectionSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InspectionLotRepository extends JpaRepository<InspectionLot, Long> {

    @Query("SELECT l FROM InspectionLot l WHERE l.id = :id AND l.deletedDate IS NULL")
    Optional<InspectionLot> findLiveById(@Param("id") Long id);

    @Query("SELECT l FROM InspectionLot l WHERE l.deletedDate IS NULL ORDER BY l.creationDate DESC")
    List<InspectionLot> findAllLive();

    @Query("SELECT l FROM InspectionLot l WHERE l.status = :status AND l.deletedDate IS NULL "
            + "ORDER BY l.creationDate DESC")
    List<InspectionLot> findLiveByStatus(@Param("status") InspectionLotStatus status);

    @Query("SELECT l FROM InspectionLot l WHERE l.source = :source AND l.deletedDate IS NULL "
            + "ORDER BY l.creationDate DESC")
    List<InspectionLot> findLiveBySource(@Param("source") InspectionSource source);

    /**
     * Every live lot raised against a work order, whatever its verdict. The gate needs the failures
     * as much as the passes — a lot that failed is the whole reason to stop.
     */
    @Query("SELECT l FROM InspectionLot l WHERE l.workOrder.id = :workOrderId "
            + "AND l.deletedDate IS NULL ORDER BY l.creationDate")
    List<InspectionLot> findLiveByWorkOrder(@Param("workOrderId") int workOrderId);

    @Query("SELECT l FROM InspectionLot l WHERE l.workOrderOperation.id = :operationId "
            + "AND l.deletedDate IS NULL ORDER BY l.creationDate")
    List<InspectionLot> findLiveByOperation(@Param("operationId") Long operationId);

    @Query("SELECT l FROM InspectionLot l WHERE l.goodsReceiptNote.id = :grnId "
            + "AND l.deletedDate IS NULL ORDER BY l.creationDate")
    List<InspectionLot> findLiveByGoodsReceiptNote(@Param("grnId") Long grnId);

    @Query("SELECT l FROM InspectionLot l WHERE l.packageBox.id = :packageBoxId "
            + "AND l.deletedDate IS NULL ORDER BY l.creationDate")
    List<InspectionLot> findLiveByPackageBox(@Param("packageBoxId") Long packageBoxId);
}
