package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.service.CoaService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.exception.InvalidVoucherException;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.TaxLineType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.events.DebitNoteConfirmedEvent;
import com.nextgenmanager.nextgenmanager.purchase.model.DebitNote;
import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import com.nextgenmanager.nextgenmanager.purchase.repository.DebitNoteRepository;
import com.nextgenmanager.nextgenmanager.purchase.service.GstResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.cr;
import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.dr;
import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.money;

/**
 * Auto-posts the DEBIT_NOTE voucher when a purchase return is confirmed.
 * The companion {@code PURCHASE_RETURN} inventory movement posts Cr stock / Dr GR-IR;
 * this voucher closes the vendor/GST side so GR-IR nets to zero:
 * <pre>
 *   Dr  Vendor sub-ledger               totalAmount
 *      Cr  GR/IR Clearing                 subtotal
 *      Cr  Input CGST+SGST (intra) / IGST (inter)   totalGstAmount
 *      Cr/Dr  Round-off                   balancing difference
 * </pre>
 * The debit note carries only a combined GST amount, so intra/inter-state is derived from
 * the vendor via {@link GstResolver} (same logic the purchase module uses).
 */
@Component
@RequiredArgsConstructor
public class DebitNotePostingListener {

    private static final Logger log = LoggerFactory.getLogger(DebitNotePostingListener.class);
    private static final String SOURCE_DOC_TYPE = SourceDocTypes.DEBIT_NOTE;
    private static final String SYSTEM_USER = "SYSTEM";

    private final DebitNoteRepository debitNoteRepo;
    private final CoaService coaService;
    private final LedgerResolver ledgers;
    private final GstResolver gstResolver;
    private final PostingService postingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDebitNoteConfirmed(DebitNoteConfirmedEvent event) {
        try {
            DebitNote dn = debitNoteRepo.findByIdAndDeletedDateIsNull(event.getDebitNoteId()).orElse(null);
            if (dn == null) {
                log.warn("Debit-note auto-post: debit note {} not found — skipped", event.getDebitNoteId());
                return;
            }
            if (dn.getVendor() == null) {
                log.warn("Debit-note auto-post: debit note {} has no vendor — skipped", dn.getDebitNoteNumber());
                return;
            }

            Contact vendor = dn.getVendor();
            LedgerAccount party = coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR);

            BigDecimal subtotal = money(dn.getSubtotal());
            BigDecimal totalGst = money(dn.getTotalGstAmount());
            BigDecimal total = money(dn.getTotalAmount());

            List<VoucherLineDraft> lines = new ArrayList<>();
            lines.add(dr(party.getId(), total, "Being purchase return " + dn.getDebitNoteNumber(), null));
            lines.add(cr(ledgers.grIrClearing().getId(), subtotal, "Purchase return " + dn.getDebitNoteNumber(), null));

            if (totalGst.signum() > 0) {
                GstTreatment treatment = gstResolver.deriveGstTreatment(vendor);
                if (treatment == GstTreatment.INTRA_STATE) {
                    BigDecimal cgst = totalGst.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    BigDecimal sgst = totalGst.subtract(cgst);
                    lines.add(cr(ledgers.inputCgst().getId(), cgst, "Input CGST reversal", TaxLineType.CGST));
                    lines.add(cr(ledgers.inputSgst().getId(), sgst, "Input SGST reversal", TaxLineType.SGST));
                } else {
                    lines.add(cr(ledgers.inputIgst().getId(), totalGst, "Input IGST reversal", TaxLineType.IGST));
                }
            }

            PostingSupport.balanceWithRoundOff(lines, ledgers);

            VoucherDraft draft = new VoucherDraft();
            draft.setVoucherType(VoucherType.DEBIT_NOTE);
            draft.setDate(dn.getDebitNoteDate());
            draft.setNarration("Debit Note " + dn.getDebitNoteNumber() + " - " + vendor.getCompanyName());
            draft.setSourceDocType(SOURCE_DOC_TYPE);
            draft.setSourceDocId(dn.getId());
            draft.setLines(lines);

            VoucherDto voucher = postingService.post(draft, SYSTEM_USER);
            log.info("Auto-posted DEBIT_NOTE voucher {} for debit note {}", voucher.getVoucherNumber(), dn.getDebitNoteNumber());

        } catch (InvalidVoucherException e) {
            log.warn("Debit-note auto-post skipped for debit note {}: {}", event.getDebitNoteId(), e.getMessage());
        } catch (Exception e) {
            log.error("Debit-note auto-post FAILED for debit note {} — GL not updated, manual posting required",
                    event.getDebitNoteId(), e);
        }
    }
}
