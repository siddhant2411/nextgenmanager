package com.nextgenmanager.nextgenmanager.production.service.audit;

public interface RoutingAuditService {
    void audit(String action, String actor, String details);
}
