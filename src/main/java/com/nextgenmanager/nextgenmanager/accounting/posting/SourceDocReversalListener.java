package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.common.audit.AuditActions;
import com.nextgenmanager.nextgenmanager.common.audit.AuditEvent;
import com.nextgenmanager.nextgenmanager.common.audit.service.AuditTrailService;
import com.nextgenmanager.nextgenmanager.common.events.DocumentVoidedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reverses the auto-posted voucher when its source document is cancelled/deleted, so a voided
 * document never leaves a stranded GL entry. Runs AFTER_COMMIT in its own transaction; a failure
 * never rolls back the source-document change. Idempotent: a voucher already REVERSED (or never
 * posted) is a no-op.
 *
 * <p>The reversal is made in the name of the person who cancelled the document. The cancellation and
 * any failure to reverse are written to the audit trail in transactions of their own, so they survive
 * a reversal that rolls back — a failure shows on the Accounting home page until someone reverses the
 * voucher by hand.
 */
@Component
@RequiredArgsConstructor
public class SourceDocReversalListener {

    private static final Logger log = LoggerFactory.getLogger(SourceDocReversalListener.class);
    private static final String SYSTEM_USER = "SYSTEM";

    private final VoucherRepository voucherRepo;
    private final PostingService postingService;
    private final AuditTrailService auditTrail;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDocumentVoided(DocumentVoidedEvent event) {
        String type = event.getSourceDocType();
        Long id = event.getSourceDocId();
        String user = event.getVoidedBy() != null ? event.getVoidedBy() : SYSTEM_USER;
        String reason = event.getReason() != null ? event.getReason() : "Source document voided";
        Voucher voucher = null;
        try {
            voucher = voucherRepo
                    .findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(type, id)
                    .orElse(null);
            auditTrail.recordInNewTransaction(AuditEvent.of(AuditActions.VOID, type, id)
                    .label(type + " #" + id).summary(reason)
                    .voucher(voucher != null ? voucher.getId() : null).by(user));
            if (voucher == null) {
                log.debug("Reversal: no voucher for voided {}#{} — nothing to reverse", type, id);
                return;
            }
            if (voucher.getStatus() != VoucherStatus.POSTED) {
                // Already REVERSED, or PENDING_APPROVAL/DRAFT (never hit the ledger).
                log.info("Reversal: voucher {} for {}#{} is {} — skipped",
                        voucher.getVoucherNumber(), type, id, voucher.getStatus());
                return;
            }
            VoucherDto reversal = postingService.reverse(voucher.getId(), reason, user);
            log.info("Auto-reversed voucher {} (via {}) for voided {}#{}",
                    voucher.getVoucherNumber(), reversal.getVoucherNumber(), type, id);

        } catch (Exception e) {
            log.error("Auto-reversal FAILED for voided {}#{} — GL not updated, manual reversal required",
                    type, id, e);
            try {
                auditTrail.recordInNewTransaction(AuditEvent.of(AuditActions.AUTO_REVERSAL_FAILED, type, id)
                        .label(voucher != null ? voucher.getVoucherNumber() : type + " #" + id)
                        .summary("Could not reverse automatically: " + e.getMessage())
                        .voucher(voucher != null ? voucher.getId() : null).by(user));
            } catch (Exception again) {
                log.error("Could not record the failed auto-reversal for {}#{}", type, id, again);
            }
        }
    }
}
