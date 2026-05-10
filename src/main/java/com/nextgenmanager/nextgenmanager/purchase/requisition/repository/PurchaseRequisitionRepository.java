package com.nextgenmanager.nextgenmanager.purchase.requisition.repository;

import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisition;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalStatus;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseRequisitionRepository extends JpaRepository<PurchaseRequisition, Long> {

    Page<PurchaseRequisition> findByDeletedDateIsNull(Pageable pageable);

    Page<PurchaseRequisition> findByStatusAndDeletedDateIsNull(PurchaseRequisitionStatus status, Pageable pageable);

    Page<PurchaseRequisition> findByApprovalStatusAndDeletedDateIsNull(PurchaseRequisitionApprovalStatus approvalStatus, Pageable pageable);

    Optional<PurchaseRequisition> findByIdAndDeletedDateIsNull(Long id);

    boolean existsByPrNumber(String prNumber);
}
