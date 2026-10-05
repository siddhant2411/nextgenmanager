package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.dto.auth.AuthAgreementResponse;
import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.model.UserAgreementAcceptance;
import com.nextgenmanager.nextgenmanager.common.repository.AppUserRepository;
import com.nextgenmanager.nextgenmanager.common.repository.UserAgreementAcceptanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * The user agreement every user accepts before using the application.
 *
 * <p>The text is read from {@code app.agreement.location}, so a deployment can ship its own
 * agreement (e.g. {@code file:/config/user-agreement.html}) without a rebuild. Changing the text
 * means bumping {@code app.agreement.version}: acceptance is recorded per version, so the bump
 * asks every user to accept again while keeping the record of what they accepted before.
 */
@Service
public class UserAgreementService {
    private static final Logger logger = LoggerFactory.getLogger(UserAgreementService.class);

    private final AppUserRepository appUserRepository;
    private final UserAgreementAcceptanceRepository acceptanceRepository;
    private final ResourceLoader resourceLoader;
    private final String version;
    private final String title;
    private final String location;

    private volatile String cachedContent;

    public UserAgreementService(
            AppUserRepository appUserRepository,
            UserAgreementAcceptanceRepository acceptanceRepository,
            ResourceLoader resourceLoader,
            @Value("${app.agreement.version:1.0}") String version,
            @Value("${app.agreement.title:User Agreement}") String title,
            @Value("${app.agreement.location:classpath:agreement/user-agreement.html}") String location
    ) {
        this.appUserRepository = appUserRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.resourceLoader = resourceLoader;
        this.version = version.trim();
        this.title = title;
        this.location = location;
    }

    public String getCurrentVersion() {
        return version;
    }

    public boolean hasAcceptedCurrent(String username) {
        AppUser user = findUser(username);
        return acceptanceRepository.findByAppUser_IdAndAgreementVersion(user.getId(), version).isPresent();
    }

    public AuthAgreementResponse getAgreement(String username) {
        AppUser user = findUser(username);
        Date acceptedDate = acceptanceRepository.findByAppUser_IdAndAgreementVersion(user.getId(), version)
                .map(UserAgreementAcceptance::getAcceptedDate)
                .orElse(null);
        return new AuthAgreementResponse(version, title, loadContent(), acceptedDate != null, acceptedDate);
    }

    /**
     * Records acceptance of the current version. Accepting twice is harmless and keeps the first
     * record, since that is the moment the user actually agreed.
     */
    public AuthAgreementResponse accept(String username, String acceptedVersion, String ipAddress, String userAgent) {
        if (acceptedVersion == null || acceptedVersion.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "version is required");
        }
        if (!version.equals(acceptedVersion.trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The user agreement has changed since this page was opened. Please review the current version.");
        }

        AppUser user = findUser(username);
        if (acceptanceRepository.findByAppUser_IdAndAgreementVersion(user.getId(), version).isEmpty()) {
            UserAgreementAcceptance acceptance = new UserAgreementAcceptance();
            acceptance.setAppUser(user);
            acceptance.setAgreementVersion(version);
            acceptance.setAcceptedDate(new Date());
            acceptance.setIpAddress(truncate(ipAddress, 64));
            acceptance.setUserAgent(truncate(userAgent, 500));
            try {
                acceptanceRepository.save(acceptance);
                logger.info("User {} accepted user agreement version {}", username, version);
            } catch (DataIntegrityViolationException ex) {
                // A double-click raced us to the unique (userId, agreementVersion) row; the first one stands.
                logger.debug("Agreement acceptance already recorded for user {} version {}", username, version);
            }
        }
        return getAgreement(username);
    }

    private AppUser findUser(String username) {
        return appUserRepository.findByUsernameAndDeletedDateIsNull(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
    }

    private String loadContent() {
        String content = cachedContent;
        if (content == null) {
            Resource resource = resourceLoader.getResource(location);
            try (InputStream in = resource.getInputStream()) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                logger.error("User agreement could not be read from {}", location, ex);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The user agreement is not configured on this server. Contact your administrator.");
            }
            cachedContent = content;
        }
        return content;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
