package com.nextgenmanager.nextgenmanager.common.repository;

import com.nextgenmanager.nextgenmanager.common.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenAndRevokedFalseAndDeletedDateIsNull(String token);

    List<RefreshToken> findByAppUser_IdAndRevokedFalseAndDeletedDateIsNull(Long appUserId);
}
