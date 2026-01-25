package com.nextgenmanager.nextgenmanager.production.repository.audit;

import com.nextgenmanager.nextgenmanager.production.model.audit.RoutingAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingAuditRepository extends JpaRepository<RoutingAudit,Long> {
}
