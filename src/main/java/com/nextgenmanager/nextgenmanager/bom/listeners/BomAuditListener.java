package com.nextgenmanager.nextgenmanager.bom.listeners;

import com.nextgenmanager.nextgenmanager.bom.events.BomCreatedEvent;
import com.nextgenmanager.nextgenmanager.bom.events.BomModifiedEvent;
import com.nextgenmanager.nextgenmanager.bom.events.BomStatusChangedEvent;
import com.nextgenmanager.nextgenmanager.bom.model.BomAudit;
import com.nextgenmanager.nextgenmanager.bom.repository.BomAuditRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;


@Component
public class BomAuditListener {

    private final BomAuditRepository auditRepository;

    @Autowired
    public BomAuditListener(BomAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @EventListener
    public void on(BomStatusChangedEvent event) {

        BomAudit audit = new BomAudit();

        audit.setBomId(event.getBomId());
        audit.setOldStatus(event.getOldStatus());
        audit.setNewStatus(event.getNewStatus());
        audit.setChangedAt(event.getOccurredAt());

        // If you implement user context later, replace with username
        audit.setChangedBy("SYSTEM");

        // Where this came from — API/UI/SYSTEM (optional but very valuable)
        audit.setSource("SYSTEM");

        // Optional comments (for approval/reject flows)
        audit.setComment(null);

        // Store raw event JSON if needed (future-ready)
        audit.setPayloadJson(null);

        auditRepository.save(audit);
    }

    @EventListener
    public void on(BomCreatedEvent event) {
        BomAudit audit = new BomAudit();
        audit.setBomId(event.getBomId());
        audit.setOldStatus(null);
        audit.setNewStatus(event.getStatus());
        audit.setChangedAt(event.getOccurredAt());
        audit.setChangedBy("SYSTEM");
        audit.setSource("SYSTEM");
        audit.setComment("BOM created");
        auditRepository.save(audit);
    }
    @EventListener
    public void on(BomModifiedEvent event) {

        BomAudit audit = new BomAudit();

        audit.setBomId(event.getBomId());

        // No status change – but we still log the state during modification
        audit.setOldStatus(event.getStatus());
        audit.setNewStatus(event.getStatus());

        audit.setChangedAt(event.getOccurredAt());
        audit.setChangedBy("SYSTEM");
        audit.setSource("SYSTEM");

        audit.setComment("BOM modified (draft updated)");

        audit.setPayloadJson(null); // optional

        auditRepository.save(audit);
    }

}