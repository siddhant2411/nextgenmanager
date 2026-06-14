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
import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import com.nextgenmanager.nextgenmanager.purchase.service.GstResolver;
import com.nextgenmanager.nextgenmanager.sales.events.SalesCreditNoteConfirmedEvent;
import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNote;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesCreditNoteRepository;
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
 * Auto-posts the CREDIT_NOTE voucher when a sales credit note is confirmed — the mirror of
 * a sales invoice, reversing the original output liability:
 * <pre>
 *   Dr  Sales - Domestic                subtotal
 *   Dr  Output CGST+SGST (intra) / IGST (inter)   totalGstAmount
 *      Cr  Customer sub-ledger             totalAmount
 *      Cr/Dr  Round-off                    balancing difference
 * </pre>
 * The credit note carries only a combined GST amount, so intra/inter-state is derived from
 * the customer via {@link GstResolver}.
 */
@Component
@RequiredArgsConstructor
public class SalesCreditNotePostingListener {

    private static final Logger log = LoggerFactory.getLogger(SalesCreditNotePostingListener.class);
    private static final String SOURCE_DOC_TYPE = SourceDocTypes.SALES_CREDIT_NOTE;
    private static final String SYSTEM_USER = "SYSTEM";

    private final SalesCreditNoteRepository creditNoteRepo;
    private final CoaService coaService;
    private final LedgerResolver ledgers;
    private final GstResolver gstResolver;
    private final PostingService postingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSalesCreditNoteConfirmed(SalesCreditNoteConfirmedEvent event) {
        try {
            SalesCreditNote cn = creditNoteRepo.findByIdAndDeletedDateIsNull(event.getSalesCreditNoteId()).orElse(null);
            if (cn == null) {
                log.warn("Credit-note auto-post: credit note {} not found — skipped", event.getSalesCreditNoteId());
                return;
            }
            if (cn.getCustomer() == null) {
                log.warn("Credit-note auto-post: credit note {} has no customer — skipped", cn.getCreditNoteNumber());
                return;
            }

            Contact customer = cn.getCustomer();
            LedgerAccount party = coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER);

            BigDecimal subtotal = money(cn.getSubtotal());
            BigDecimal totalGst = money(cn.getTotalGstAmount());
            BigDecimal total = money(cn.getTotalAmount());

            List<VoucherLineDraft> lines = new ArrayList<>();
            lines.add(dr(ledgers.salesDomestic().getId(), subtotal, "Sales return " + cn.getCreditNoteNumber(), null));

            if (totalGst.signum() > 0) {
                GstTreatment treatment = gstResolver.deriveGstTreatment(customer);
                if (treatment == GstTreatment.INTRA_STATE) {
                    BigDecimal cgst = totalGst.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    BigDecimal sgst = totalGst.subtract(cgst);
                    lines.add(dr(ledgers.outputCgst().getId(), cgst, "Output CGST reversal", TaxLineType.CGST));
                    lines.add(dr(ledgers.outputSgst().getId(), sgst, "Output SGST reversal", TaxLineType.SGST));
                } else {
                    lines.add(dr(ledgers.outputIgst().getId(), totalGst, "Output IGST reversal", TaxLineType.IGST));
                }
            }

            lines.add(cr(party.getId(), total, "Being sales credit note " + cn.getCreditNoteNumber(), null));

            PostingSupport.balanceWithRoundOff(lines, ledgers);

            VoucherDraft draft = new VoucherDraft();
            draft.setVoucherType(VoucherType.CREDIT_NOTE);
            draft.setDate(cn.getCreditNoteDate());
            draft.setNarration("Credit Note " + cn.getCreditNoteNumber() + " - " + customer.getCompanyName());
            draft.setSourceDocType(SOURCE_DOC_TYPE);
            draft.setSourceDocId(cn.getId());
            draft.setLines(lines);

            VoucherDto voucher = postingService.post(draft, SYSTEM_USER);
            log.info("Auto-posted CREDIT_NOTE voucher {} for credit note {}", voucher.getVoucherNumber(), cn.getCreditNoteNumber());

        } catch (InvalidVoucherException e) {
            log.warn("Credit-note auto-post skipped for credit note {}: {}", event.getSalesCreditNoteId(), e.getMessage());
        } catch (Exception e) {
            log.error("Credit-note auto-post FAILED for credit note {} — GL not updated, manual posting required",
                    event.getSalesCreditNoteId(), e);
        }
    }
}
