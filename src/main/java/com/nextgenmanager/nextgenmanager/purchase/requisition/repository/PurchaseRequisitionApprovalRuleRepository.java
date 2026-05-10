package com.nextgenmanager.nextgenmanager.purchase.requisition.repository;

import com.nextgenmanager.nextgenmanager.purchase.requisition.model.PurchaseRequisitionApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseRequisitionApprovalRuleRepository extends JpaRepository<PurchaseRequisitionApprovalRule, Long> {

    List<PurchaseRequisitionApprovalRule> findByActiveTrue();
}
