package com.nextgenmanager.nextgenmanager.purchase.requisition.repository;

import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisition;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionItem;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionItemStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {

    /**
     * Lines of live, approved requisitions for any of {@code itemIds} that are still {@code lineStatus}
     * and whose requisition is not in {@code closed}: supply asked for but not yet ordered.
     */
    @Query("SELECT l FROM PurchaseRequisitionItem l JOIN FETCH l.requisition r "
            + "WHERE l.item.inventoryItemId IN :itemIds AND l.lineStatus = :lineStatus "
            + "AND r.approvalStatus = :approved AND r.status NOT IN :closed AND r.deletedDate IS NULL")
    java.util.List<PurchaseRequisitionItem> findOpenLinesForItems(
            @Param("itemIds") java.util.Collection<Integer> itemIds,
            @Param("lineStatus") PurchaseRequisitionItemStatus lineStatus,
            @Param("approved") PurchaseRequisitionApprovalStatus approved,
            @Param("closed") java.util.List<PurchaseRequisitionStatus> closed);

    Page<PurchaseRequisition> findByDeletedDateIsNull(Pageable pageable);

    Page<PurchaseRequisition> findByStatusAndDeletedDateIsNull(PurchaseRequisitionStatus status, Pageable pageable);

    Page<PurchaseRequisition> findByApprovalStatusAndDeletedDateIsNull(PurchaseRequisitionApprovalStatus approvalStatus, Pageable pageable);

    Optional<PurchaseRequisition> findByIdAndDeletedDateIsNull(Long id);

    boolean existsByPrNumber(String prNumber);

    /** Requisitions raised on behalf of another document, e.g. a sales order's shortfall. */
    java.util.List<PurchaseRequisition> findBySourceAndSourceRefIdAndDeletedDateIsNull(
            com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionSource source,
            Long sourceRefId);
}
