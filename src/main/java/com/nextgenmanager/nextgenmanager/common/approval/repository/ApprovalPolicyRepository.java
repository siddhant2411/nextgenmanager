package com.nextgenmanager.nextgenmanager.common.approval.repository;

import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicy, Long> {

    @Query("SELECT p FROM ApprovalPolicy p LEFT JOIN FETCH p.rules r " +
           "WHERE p.documentType = :type AND p.deletedDate IS NULL AND p.enabled = true")
    Optional<ApprovalPolicy> findEnabledWithRules(@Param("type") String documentType);

    Optional<ApprovalPolicy> findByDocumentTypeAndDeletedDateIsNull(String documentType);
}
