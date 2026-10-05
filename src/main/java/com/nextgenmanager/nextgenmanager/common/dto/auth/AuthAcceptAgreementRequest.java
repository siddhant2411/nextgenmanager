package com.nextgenmanager.nextgenmanager.common.dto.auth;

/**
 * The version the user was shown. It must match the server's current version, so a page left open
 * across an agreement change cannot record acceptance of text the user never saw.
 */
public record AuthAcceptAgreementRequest(
        String version
) {
}
