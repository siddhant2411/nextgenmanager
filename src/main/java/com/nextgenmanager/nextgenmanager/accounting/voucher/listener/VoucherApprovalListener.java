package com.nextgenmanager.nextgenmanager.accounting.voucher.listener;

import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.common.approval.event.ApprovalCompletedEvent;
import com.nextgenmanager.nextgenmanager.common.audit.AuditActions;
import com.nextgenmanager.nextgenmanager.common.audit.AuditEvent;
import com.nextgenmanager.nextgenmanager.common.audit.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class VoucherApprovalListener {

    private static final Logger log = LoggerFactory.getLogger(VoucherApprovalListener.class);
    private static final String MANUAL_VOUCHER = "MANUAL_VOUCHER";

    private final VoucherRepository voucherRepo;
    private final AuditTrailService auditTrail;

    @EventListener
    @Transactional
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        if (!MANUAL_VOUCHER.equals(event.getDocumentType())) return;

        voucherRepo.findById(event.getDocumentId()).ifPresent(v -> {
            if (v.getStatus() == VoucherStatus.PENDING_APPROVAL) {
                v.setStatus(VoucherStatus.POSTED);
                voucherRepo.save(v);
                auditTrail.record(AuditEvent.of(AuditActions.APPROVE, AuditActions.VOUCHER, v.getId())
                        .label(v.getVoucherNumber()).summary("Approved and posted").voucher(v.getId())
                        .by(event.getApprovedBy()));
                log.info("Voucher {} promoted to POSTED after approval by {}",
                        v.getVoucherNumber(), event.getApprovedBy());
            }
        });
    }
}
