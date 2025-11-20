package com.nextgenmanager.nextgenmanager.component.listeners;

import com.nextgenmanager.nextgenmanager.component.events.ComponentUpdatedEvent;
import com.nextgenmanager.nextgenmanager.component.model.ComponentAudit;
import com.nextgenmanager.nextgenmanager.component.repository.ComponentAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ComponentAuditListener {

    private static final Logger log = LoggerFactory.getLogger(ComponentAuditListener.class);

    private final ComponentAuditRepository auditRepo;

    @Autowired
    public ComponentAuditListener(ComponentAuditRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @EventListener
    public void on(ComponentUpdatedEvent event) {

        log.info("AUDIT | Component {} updated | Field: {} | {} → {}",
                event.getComponentId(),
                event.getFieldChanged(),
                event.getOldValue(),
                event.getNewValue()
        );

        ComponentAudit audit = new ComponentAudit();
        audit.setComponentId(event.getComponentId());
        audit.setFieldChanged(event.getFieldChanged());
        audit.setOldValue(event.getOldValue());
        audit.setNewValue(event.getNewValue());

        audit.setChangedAt(event.getOccurredAt());
        audit.setChangedBy("SYSTEM");  // replace with logged-in username later
        audit.setSource("SYSTEM");
        audit.setComment(null);
        audit.setPayloadJson(null);

        auditRepo.save(audit);
    }
}
