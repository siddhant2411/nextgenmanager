package com.nextgenmanager.nextgenmanager.common.approval.repository;

import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {
    List<ApprovalRule> findByPolicyIdAndDeletedDateIsNull(Long policyId);
}
