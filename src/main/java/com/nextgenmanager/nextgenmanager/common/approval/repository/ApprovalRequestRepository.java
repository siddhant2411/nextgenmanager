package com.nextgenmanager.nextgenmanager.common.approval.repository;

import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalRequest;
import com.nextgenmanager.nextgenmanager.common.approval.model.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Optional<ApprovalRequest> findByDocumentTypeAndDocumentId(String documentType, Long documentId);

    List<ApprovalRequest> findByStatus(ApprovalStatus status);

    @Query("SELECT ar FROM ApprovalRequest ar JOIN FETCH ar.steps WHERE ar.id = :id")
    Optional<ApprovalRequest> findByIdWithSteps(@Param("id") Long id);
}
