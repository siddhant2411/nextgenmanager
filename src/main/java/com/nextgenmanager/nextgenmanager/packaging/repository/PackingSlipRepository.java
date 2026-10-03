package com.nextgenmanager.nextgenmanager.packaging.repository;

import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackingSlipRepository extends JpaRepository<PackingSlip, Long> {

    @Query("SELECT s FROM PackingSlip s WHERE s.id = :id AND s.deletedDate IS NULL")
    Optional<PackingSlip> findLiveById(@Param("id") Long id);

    @Query("SELECT s FROM PackingSlip s WHERE s.deletedDate IS NULL ORDER BY s.creationDate DESC")
    List<PackingSlip> findAllLive();

    @Query("SELECT s FROM PackingSlip s WHERE s.status = :status AND s.deletedDate IS NULL "
            + "ORDER BY s.creationDate DESC")
    List<PackingSlip> findLiveByStatus(@Param("status") PackingSlipStatus status);

    @Query("SELECT s FROM PackingSlip s WHERE s.salesOrder.id = :salesOrderId AND s.deletedDate IS NULL "
            + "ORDER BY s.creationDate DESC")
    List<PackingSlip> findLiveBySalesOrder(@Param("salesOrderId") Long salesOrderId);

    /**
     * The live slip packing this pick, if any — at most one, which is how that is enforced in the
     * service. A cancelled slip does not count: cancelling released its box allocations, so the
     * pick is free to be packed again.
     */
    @Query("SELECT s FROM PackingSlip s WHERE s.pickList.id = :pickListId AND s.deletedDate IS NULL "
            + "AND s.status <> com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus.CANCELLED")
    Optional<PackingSlip> findLiveByPickList(@Param("pickListId") Long pickListId);

    /** The slip a delivery note shipped, for showing the two documents against each other. */
    @Query("SELECT s FROM PackingSlip s WHERE s.deliveryNote.id = :deliveryNoteId AND s.deletedDate IS NULL")
    Optional<PackingSlip> findByDeliveryNote(@Param("deliveryNoteId") Long deliveryNoteId);
}
