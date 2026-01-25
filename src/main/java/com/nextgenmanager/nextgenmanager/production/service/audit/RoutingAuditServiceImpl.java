package com.nextgenmanager.nextgenmanager.production.service.audit;

import com.nextgenmanager.nextgenmanager.production.model.audit.RoutingAudit;
import com.nextgenmanager.nextgenmanager.production.repository.audit.RoutingAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RoutingAuditServiceImpl implements RoutingAuditService{

    @Autowired
    private RoutingAuditRepository routingAuditRepository;

    @Override
    public void audit(String action, String actor, String details) {

        RoutingAudit log = new RoutingAudit();
        log.setAction(action);
        log.setActor(actor);
        log.setDetails(details);
        routingAuditRepository.save(log);

    }
}
