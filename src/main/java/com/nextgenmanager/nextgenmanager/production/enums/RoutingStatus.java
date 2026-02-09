package com.nextgenmanager.nextgenmanager.production.enums;

public enum RoutingStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    ACTIVE,      // Effective for production
    SUPERSEDED,  // newer active version exists
    OBSOLETE,
    ARCHIVED
}
