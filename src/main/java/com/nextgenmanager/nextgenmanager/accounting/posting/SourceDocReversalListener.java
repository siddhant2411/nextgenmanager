package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
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
 * logs an alert but never rolls back the source-document change. Idempotent: a voucher already
 * REVERSED (or never posted) is a no-op.
 */
@Component
@RequiredArgsConstructor
public class SourceDocReversalListener {

    private static final Logger log = LoggerFactory.getLogger(SourceDocReversalListener.class);
    private static final String SYSTEM_USER = "SYSTEM";

    private final VoucherRepository voucherRepo;
    private final PostingService postingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDocumentVoided(DocumentVoidedEvent event) {
        String type = event.getSourceDocType();
        Long id = event.getSourceDocId();
        try {
            Voucher voucher = voucherRepo
                    .findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(type, id)
                    .orElse(null);
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
            String reason = event.getReason() != null ? event.getReason() : "Source document voided";
            VoucherDto reversal = postingService.reverse(voucher.getId(), reason, SYSTEM_USER);
            log.info("Auto-reversed voucher {} (via {}) for voided {}#{}",
                    voucher.getVoucherNumber(), reversal.getVoucherNumber(), type, id);

        } catch (Exception e) {
            log.error("Auto-reversal FAILED for voided {}#{} — GL not updated, manual reversal required",
                    type, id, e);
        }
    }
}
