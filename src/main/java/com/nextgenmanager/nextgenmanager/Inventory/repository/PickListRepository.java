package com.nextgenmanager.nextgenmanager.Inventory.repository;

import com.nextgenmanager.nextgenmanager.Inventory.model.PickList;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PickListRepository extends JpaRepository<PickList, Long> {

    @Query("SELECT p FROM PickList p WHERE p.id = :id AND p.deletedDate IS NULL")
    Optional<PickList> findLiveById(@Param("id") Long id);

    @Query("SELECT p FROM PickList p WHERE p.deletedDate IS NULL ORDER BY p.creationDate DESC")
    List<PickList> findAllLive();

    @Query("SELECT p FROM PickList p WHERE p.status = :status AND p.deletedDate IS NULL "
            + "ORDER BY p.creationDate DESC")
    List<PickList> findLiveByStatus(@Param("status") PickListStatus status);

    @Query("SELECT p FROM PickList p WHERE p.salesOrder.id = :salesOrderId AND p.deletedDate IS NULL "
            + "ORDER BY p.creationDate DESC")
    List<PickList> findLiveBySalesOrder(@Param("salesOrderId") Long salesOrderId);

    /**
     * Picks waiting for a delivery note on this order. A dispatcher who ignores one of these and
     * raises a delivery note by hand would allocate a second set of units for stock already on a
     * trolley, so the delivery note refuses unless the bypass is asked for by name.
     */
    @Query("SELECT p FROM PickList p WHERE p.salesOrder.id = :salesOrderId "
            + "AND p.status = com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus.PICKED "
            + "AND p.deletedDate IS NULL ORDER BY p.pickedDate")
    List<PickList> findPickedAwaitingDispatch(@Param("salesOrderId") Long salesOrderId);

    /** The pick a delivery note consumed, for showing the two documents against each other. */
    @Query("SELECT p FROM PickList p WHERE p.deliveryNote.id = :deliveryNoteId AND p.deletedDate IS NULL")
    Optional<PickList> findByDeliveryNote(@Param("deliveryNoteId") Long deliveryNoteId);

    /**
     * How much of an item is already spoken for by other live picks on this order. Cancelled picks
     * are excluded, since their stock went back on the shelf.
     *
     * <p>A pick still on the floor speaks for what it was asked to take. Once it is confirmed it
     * speaks only for what was actually found: counting the asked quantity of a short pick would
     * leave the shortfall owned by a pick that is finished, and nothing could ever pick it again.
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN p.status IN ("
            + "com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus.PICKED, "
            + "com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus.DISPATCHED) "
            + "THEN l.quantityPicked ELSE l.quantityToPick END), 0) FROM PickList p JOIN p.lines l "
            + "WHERE p.salesOrder.id = :salesOrderId "
            + "AND l.inventoryItem.inventoryItemId = :itemId "
            + "AND p.deletedDate IS NULL "
            + "AND p.status <> com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus.CANCELLED")
    BigDecimal sumAlreadyOnPicks(@Param("salesOrderId") Long salesOrderId, @Param("itemId") int itemId);
}
