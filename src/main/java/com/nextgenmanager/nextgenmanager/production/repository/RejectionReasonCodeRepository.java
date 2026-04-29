package com.nextgenmanager.nextgenmanager.production.repository;

import com.nextgenmanager.nextgenmanager.production.enums.ReasonCodeCategory;
import com.nextgenmanager.nextgenmanager.production.model.RejectionReasonCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RejectionReasonCodeRepository extends JpaRepository<RejectionReasonCode, Long> {

    Optional<RejectionReasonCode> findByCode(String code);

    List<RejectionReasonCode> findByIsActiveTrue();

    List<RejectionReasonCode> findByCategoryAndIsActiveTrue(ReasonCodeCategory category);

    List<RejectionReasonCode> findByCategoryInAndIsActiveTrue(List<ReasonCodeCategory> categories);
}
