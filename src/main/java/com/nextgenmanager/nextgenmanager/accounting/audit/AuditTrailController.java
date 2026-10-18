package com.nextgenmanager.nextgenmanager.accounting.audit;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.common.audit.AuditActions;
import com.nextgenmanager.nextgenmanager.common.audit.model.AuditTrailEntry;
import com.nextgenmanager.nextgenmanager.common.audit.service.AuditTrailService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAccountingAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * The audit trail (edit log) as an auditor reads it: every change to the books and the masters
 * behind them, with who made it and when. Read-only — there is no way to change an entry.
 */
@RestController
@RequestMapping("/api/accounting/audit-trail")
@RequiresAccountingAccess
@RequiredArgsConstructor
public class AuditTrailController {

    /** A register longer than this is a sign the filters are too wide, not something to page through. */
    static final int MAX_ROWS = 2000;

    private final AuditTrailService auditTrail;
    private final VoucherRepository voucherRepo;

    public record AuditTrailEntryDto(Long id, Date eventTime, String username, String action, String entityType,
                                     Long entityId, String entityLabel, String summary, String details, Long voucherId) {
        static AuditTrailEntryDto of(AuditTrailEntry a) {
            return new AuditTrailEntryDto(a.getId(), a.getEventTime(), a.getUsername(), a.getAction(), a.getEntityType(),
                    a.getEntityId(), a.getEntityLabel(), a.getSummary(), a.getDetails(), a.getVoucherId());
        }
    }

    public record AuditTrailPage(List<AuditTrailEntryDto> entries, boolean truncated) {}

    @GetMapping
    public ResponseEntity<AuditTrailPage> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username) {
        if (to.isBefore(from)) throw new IllegalArgumentException("The end date is before the start date.");
        List<AuditTrailEntry> rows = auditTrail.search(from, to, entityType, action, username);
        boolean truncated = rows.size() > MAX_ROWS;
        List<AuditTrailEntryDto> out = rows.stream().limit(MAX_ROWS).map(AuditTrailEntryDto::of).toList();
        return ResponseEntity.ok(new AuditTrailPage(out, truncated));
    }

    /** Everything that happened to one record, oldest first — a ledger's edits, a voucher's life. */
    @GetMapping("/history")
    public ResponseEntity<List<AuditTrailEntryDto>> history(@RequestParam String entityType, @RequestParam Long entityId) {
        return ResponseEntity.ok(auditTrail.history(entityType, entityId).stream().map(AuditTrailEntryDto::of).toList());
    }

    /**
     * Automatic reversals that failed and are still outstanding: the document was cancelled but its
     * voucher is still posted. One row per voucher (the latest failure); cleared once it is reversed.
     */
    @GetMapping("/failed-reversals")
    public ResponseEntity<List<AuditTrailEntryDto>> failedReversals() {
        Map<String, AuditTrailEntry> latest = new LinkedHashMap<>();
        for (AuditTrailEntry a : auditTrail.byAction(AuditActions.AUTO_REVERSAL_FAILED)) {
            String key = a.getVoucherId() != null ? "v" + a.getVoucherId() : a.getEntityType() + "#" + a.getEntityId();
            latest.putIfAbsent(key, a); // newest first, so the first seen is the latest
        }
        List<AuditTrailEntryDto> open = latest.values().stream()
                .filter(a -> a.getVoucherId() == null || voucherRepo.findById(a.getVoucherId())
                        .map(v -> v.getStatus() == VoucherStatus.POSTED).orElse(false))
                .map(AuditTrailEntryDto::of)
                .toList();
        return ResponseEntity.ok(open);
    }
}
