package com.nextgenmanager.nextgenmanager.purchase.repository;

import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PurchaseOrderApprovalRuleRepository extends JpaRepository<PurchaseOrderApprovalRule, Long> {

    List<PurchaseOrderApprovalRule> findByActiveTrue();
}
