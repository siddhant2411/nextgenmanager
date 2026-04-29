package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.model.RefreshToken;
import com.nextgenmanager.nextgenmanager.common.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void issueToken(AppUser user, String token, Date expiryDate, String actor) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAppUser(user);
        refreshToken.setToken(token);
        refreshToken.setExpiryDate(expiryDate);
        refreshToken.setRevoked(false);
        refreshToken.setCreatedBy(actor);
        refreshToken.setUpdatedBy(actor);
        try {
            refreshTokenRepository.save(refreshToken);
            logger.debug("Refresh token issued for user: {}, tokenLength: {}", user.getUsername(), token.length());
        } catch (Exception ex) {
            logger.error("Failed to store refresh token for user: {}", user.getUsername(), ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findActiveToken(String token) {
        logger.debug("Looking for refresh token, length: {}", token.length());
        Optional<RefreshToken> result = refreshTokenRepository.findByTokenAndRevokedFalseAndDeletedDateIsNull(token);
        if (result.isEmpty()) {
            logger.warn("Refresh token not found in database");
        } else {
            logger.debug("Refresh token found, user: {}", result.get().getAppUser().getUsername());
        }
        return result;
    }

    @Transactional
    public void revokeToken(String token, String actor) {
        refreshTokenRepository.findByTokenAndRevokedFalseAndDeletedDateIsNull(token)
                .ifPresent(refreshToken -> {
                    refreshToken.setRevoked(true);
                    refreshToken.setRevokedDate(new Date());
                    refreshToken.setUpdatedBy(actor);
                    refreshTokenRepository.save(refreshToken);
                });
    }

    @Transactional
    public int revokeAllForUser(Long userId, String actor) {
        var activeTokens = refreshTokenRepository.findByAppUser_IdAndRevokedFalseAndDeletedDateIsNull(userId);
        Date now = new Date();
        for (RefreshToken token : activeTokens) {
            token.setRevoked(true);
            token.setRevokedDate(now);
            token.setUpdatedBy(actor);
        }
        refreshTokenRepository.saveAll(activeTokens);
        if (!activeTokens.isEmpty()) {
            logger.info("Revoked {} refresh token(s) for userId {}", activeTokens.size(), userId);
        }
        return activeTokens.size();
    }
}
