package com.nextgenmanager.nextgenmanager.common.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgenmanager.nextgenmanager.common.audit.AuditEvent;
import com.nextgenmanager.nextgenmanager.common.audit.model.AuditTrailEntry;
import com.nextgenmanager.nextgenmanager.common.audit.repository.AuditTrailRepository;
import com.nextgenmanager.nextgenmanager.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditTrailServiceImpl implements AuditTrailService {

    private static final int SUMMARY_MAX = 500;
    private static final int LABEL_MAX = 200;

    private final AuditTrailRepository repo;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEvent event) {
        repo.save(toEntry(event));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTransaction(AuditEvent event) {
        repo.save(toEntry(event));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditTrailEntry> search(LocalDate from, LocalDate to, String entityType, String action, String username) {
        ZoneId zone = ZoneId.systemDefault();
        Date start = Date.from(from.atStartOfDay(zone).toInstant());
        Date end = Date.from(to.plusDays(1).atStartOfDay(zone).toInstant());
        return repo.search(start, end, blankToNull(entityType), blankToNull(action),
                username == null || username.isBlank() ? null : username.trim().toLowerCase());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditTrailEntry> history(String entityType, Long entityId) {
        return repo.findByEntityTypeAndEntityIdOrderByEventTimeAscIdAsc(entityType, entityId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditTrailEntry> byAction(String action) {
        return repo.findByActionOrderByEventTimeDesc(action);
    }

    AuditTrailEntry toEntry(AuditEvent e) {
        AuditTrailEntry a = new AuditTrailEntry();
        a.setEventTime(new Date());
        a.setUsername(e.isUserGiven() ? e.getUsername() : CurrentUser.nameOrNull());
        a.setAction(e.getAction());
        a.setEntityType(e.getEntityType());
        a.setEntityId(e.getEntityId());
        a.setEntityLabel(cut(e.getLabel(), LABEL_MAX));
        a.setSummary(cut(e.getSummary(), SUMMARY_MAX));
        a.setDetails(json(e.getDetails()));
        a.setVoucherId(e.getVoucherId());
        return a;
    }

    private String json(Object details) {
        if (details == null) return null;
        if (details instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception ex) {
            // A detail that will not serialise must not block the change it describes; keep what we can.
            return String.valueOf(details);
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
