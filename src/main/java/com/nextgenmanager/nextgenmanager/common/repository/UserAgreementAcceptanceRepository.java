package com.nextgenmanager.nextgenmanager.common.repository;

import com.nextgenmanager.nextgenmanager.common.model.UserAgreementAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAgreementAcceptanceRepository extends JpaRepository<UserAgreementAcceptance, Long> {
    Optional<UserAgreementAcceptance> findByAppUser_IdAndAgreementVersion(Long appUserId, String agreementVersion);
}
