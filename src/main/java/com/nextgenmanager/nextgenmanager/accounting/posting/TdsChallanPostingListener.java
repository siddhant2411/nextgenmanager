package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.tds.events.TdsChallanDepositedEvent;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsChallan;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsChallanRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.exception.InvalidVoucherException;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;

import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.cr;
import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.dr;
import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.nz;

/**
 * Posts the GL entry for a TDS challan deposit:
 * <pre>
 *   Dr  TDS Payable (9015)   amount
 *      Cr  Bank                  amount
 * </pre>
 * Clears the liability accrued at deduction. Runs AFTER_COMMIT in its own transaction; idempotent.
 */
@Component
@RequiredArgsConstructor
public class TdsChallanPostingListener {

    private static final Logger log = LoggerFactory.getLogger(TdsChallanPostingListener.class);
    private static final String SOURCE_DOC_TYPE = SourceDocTypes.TDS_CHALLAN;
    private static final String SYSTEM_USER = "SYSTEM";

    private final TdsChallanRepository challanRepo;
    private final LedgerResolver ledgers;
    private final PostingService postingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onChallanDeposited(TdsChallanDepositedEvent event) {
        try {
            TdsChallan challan = challanRepo.findById(event.getChallanId()).orElse(null);
            if (challan == null) {
                log.warn("TDS challan auto-post: challan {} not found — skipped", event.getChallanId());
                return;
            }
            BigDecimal amount = nz(challan.getAmount());
            if (amount.signum() <= 0) return;

            List<VoucherLineDraft> lines = List.of(
                    dr(ledgers.tdsPayable().getId(), amount, "TDS deposited - challan " + challan.getChallanNumber(), null),
                    cr(ledgers.bankPrimary().getId(), amount, "TDS challan " + challan.getChallanNumber(), null)
            );

            VoucherDraft draft = new VoucherDraft();
            draft.setVoucherType(VoucherType.PAYMENT);
            draft.setDate(challan.getDepositDate());
            draft.setNarration("TDS deposit " + challan.getFinancialYear() + " " + challan.getQuarter()
                    + " - challan " + challan.getChallanNumber());
            draft.setSourceDocType(SOURCE_DOC_TYPE);
            draft.setSourceDocId(challan.getId());
            draft.setLines(lines);

            VoucherDto voucher = postingService.post(draft, SYSTEM_USER);
            log.info("Auto-posted TDS challan voucher {} for challan {}", voucher.getVoucherNumber(), challan.getId());

        } catch (InvalidVoucherException e) {
            log.warn("TDS challan auto-post skipped for challan {}: {}", event.getChallanId(), e.getMessage());
        } catch (Exception e) {
            log.error("TDS challan auto-post FAILED for challan {} — GL not updated, manual posting required",
                    event.getChallanId(), e);
        }
    }
}
