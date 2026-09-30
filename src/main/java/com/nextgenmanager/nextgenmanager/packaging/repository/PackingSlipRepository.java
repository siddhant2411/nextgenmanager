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

    /** A pick is packed by at most one live slip — this is how that is enforced in the service. */
    @Query("SELECT s FROM PackingSlip s WHERE s.pickList.id = :pickListId AND s.deletedDate IS NULL")
    Optional<PackingSlip> findLiveByPickList(@Param("pickListId") Long pickListId);
}
