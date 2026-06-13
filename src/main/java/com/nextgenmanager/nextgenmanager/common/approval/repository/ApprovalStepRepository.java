package com.nextgenmanager.nextgenmanager.common.approval.repository;

import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalStep;
import com.nextgenmanager.nextgenmanager.common.approval.model.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    /** All PENDING steps for a given role — the approver's inbox. */
    @Query("SELECT s FROM ApprovalStep s JOIN FETCH s.request r " +
           "WHERE s.approverRole = :role AND s.status = 'PENDING' " +
           "ORDER BY r.requestedAt ASC")
    List<ApprovalStep> findPendingByRole(@Param("role") String role);

    Optional<ApprovalStep> findByRequestIdAndLevel(Long requestId, int level);

    List<ApprovalStep> findByRequestIdOrderByLevelAsc(Long requestId);

    boolean existsByRequestIdAndStatus(Long requestId, StepStatus status);
}
