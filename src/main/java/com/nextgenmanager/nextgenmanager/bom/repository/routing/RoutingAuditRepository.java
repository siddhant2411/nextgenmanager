package com.nextgenmanager.nextgenmanager.bom.repository.routing;

import com.nextgenmanager.nextgenmanager.production.model.audit.RoutingAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingAuditRepository extends JpaRepository<RoutingAudit,Long> {
}
