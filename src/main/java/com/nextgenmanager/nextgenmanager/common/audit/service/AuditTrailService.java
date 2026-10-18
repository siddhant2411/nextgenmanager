package com.nextgenmanager.nextgenmanager.common.audit.service;

import com.nextgenmanager.nextgenmanager.common.audit.AuditEvent;
import com.nextgenmanager.nextgenmanager.common.audit.model.AuditTrailEntry;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The edit log required by Rule 3(1) of the Companies (Accounts) Rules: every change to the books and
 * the masters behind them, with who made it and when.
 */
public interface AuditTrailService {

    /**
     * Writes the event in the caller's transaction, so the change and its record commit or roll back
     * together — a change can never reach the books without its entry.
     */
    void record(AuditEvent event);

    /**
     * Writes the event in a transaction of its own. For recording a failure: the work that failed is
     * being rolled back, and the record of the failure must survive it.
     */
    void recordInNewTransaction(AuditEvent event);

    List<AuditTrailEntry> search(LocalDate from, LocalDate to, String entityType, String action, String username);

    List<AuditTrailEntry> history(String entityType, Long entityId);

    List<AuditTrailEntry> byAction(String action);

    /**
     * The fields that differ between two snapshots, as {field: {from, to}}, in the order of {@code after}.
     * Numbers compare by value, so 100 and 100.00 are not a change.
     */
    static Map<String, Object> changes(Map<String, ?> before, Map<String, ?> after) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : after.entrySet()) {
            Object was = before.get(e.getKey());
            Object now = e.getValue();
            if (!same(was, now)) {
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("from", was);
                pair.put("to", now);
                out.put(e.getKey(), pair);
            }
        }
        return out;
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof java.math.BigDecimal x && b instanceof java.math.BigDecimal y) return x.compareTo(y) == 0;
        return Objects.equals(a, b);
    }
}
