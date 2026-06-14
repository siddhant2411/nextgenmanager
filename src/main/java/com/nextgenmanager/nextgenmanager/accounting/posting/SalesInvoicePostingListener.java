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
import com.nextgenmanager.nextgenmanager.sales.events.TaxInvoiceIssuedEvent;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;
import com.nextgenmanager.nextgenmanager.sales.repository.TaxInvoiceRepository;
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

/**
 * Auto-posts the SALES voucher for a Tax Invoice:
 * <pre>
 *   Dr  Customer sub-ledger        totalPayableAmount
 *      Cr  Sales - Domestic          taxableValue
 *      Cr  Output CGST/SGST/IGST     respective tax amounts
 *      Cr/Dr  Round-off              balancing difference
 * </pre>
 *
 * Runs AFTER_COMMIT in its own transaction: a posting failure logs an alert but never
 * rolls back the committed invoice. Idempotency is enforced by {@link PostingService}
 * (unique sourceDocType+sourceDocId), so a replayed event is a benign no-op.
 */
@Component
@RequiredArgsConstructor
public class SalesInvoicePostingListener {

    private static final Logger log = LoggerFactory.getLogger(SalesInvoicePostingListener.class);
    private static final String SOURCE_DOC_TYPE = SourceDocTypes.TAX_INVOICE;
    private static final String SYSTEM_USER = "SYSTEM";

    private final TaxInvoiceRepository taxInvoiceRepo;
    private final CoaService coaService;
    private final LedgerResolver ledgers;
    private final PostingService postingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaxInvoiceIssued(TaxInvoiceIssuedEvent event) {
        try {
            TaxInvoice inv = taxInvoiceRepo.findByIdAndDeletedDateIsNull(event.getTaxInvoiceId()).orElse(null);
            if (inv == null) {
                log.warn("Sales auto-post: tax invoice {} not found (or deleted) — skipped", event.getTaxInvoiceId());
                return;
            }
            if (inv.getSalesOrder() == null || inv.getSalesOrder().getCustomer() == null) {
                log.warn("Sales auto-post: invoice {} has no customer — skipped", inv.getInvoiceNumber());
                return;
            }

            Contact customer = inv.getSalesOrder().getCustomer();
            LedgerAccount party = coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER);

            BigDecimal taxable = nz(inv.getTaxableValue());
            BigDecimal cgst = nz(inv.getCgstAmount());
            BigDecimal sgst = nz(inv.getSgstAmount());
            BigDecimal igst = nz(inv.getIgstAmount());
            BigDecimal total = nz(inv.getTotalPayableAmount());

            List<VoucherLineDraft> lines = new ArrayList<>();
            lines.add(line(party.getId(), total, BigDecimal.ZERO,
                    "Being sales invoice " + inv.getInvoiceNumber(), null));
            lines.add(line(ledgers.salesDomestic().getId(), BigDecimal.ZERO, taxable, "Sales", null));
            if (cgst.signum() > 0) lines.add(line(ledgers.outputCgst().getId(), BigDecimal.ZERO, cgst, "Output CGST", TaxLineType.CGST));
            if (sgst.signum() > 0) lines.add(line(ledgers.outputSgst().getId(), BigDecimal.ZERO, sgst, "Output SGST", TaxLineType.SGST));
            if (igst.signum() > 0) lines.add(line(ledgers.outputIgst().getId(), BigDecimal.ZERO, igst, "Output IGST", TaxLineType.IGST));

            // Round-off: customer debit may differ from taxable + taxes by a penny.
            BigDecimal diff = total.subtract(taxable.add(cgst).add(sgst).add(igst)).setScale(2, RoundingMode.HALF_UP);
            if (diff.signum() > 0) {
                lines.add(line(ledgers.roundOffIncome().getId(), BigDecimal.ZERO, diff, "Round-off", null));
            } else if (diff.signum() < 0) {
                lines.add(line(ledgers.roundOffExpense().getId(), diff.negate(), BigDecimal.ZERO, "Round-off", null));
            }

            VoucherDraft draft = new VoucherDraft();
            draft.setVoucherType(VoucherType.SALES);
            draft.setDate(inv.getInvoiceDate());
            draft.setNarration("Sales Invoice " + inv.getInvoiceNumber() + " - " + customer.getCompanyName());
            draft.setSourceDocType(SOURCE_DOC_TYPE);
            draft.setSourceDocId(inv.getId());
            draft.setLines(lines);

            VoucherDto voucher = postingService.post(draft, SYSTEM_USER);
            log.info("Auto-posted SALES voucher {} for invoice {}", voucher.getVoucherNumber(), inv.getInvoiceNumber());

        } catch (InvalidVoucherException e) {
            // Includes the idempotency conflict (voucher already exists) — benign on replay.
            log.warn("Sales auto-post skipped for invoice {}: {}", event.getTaxInvoiceId(), e.getMessage());
        } catch (Exception e) {
            log.error("Sales auto-post FAILED for invoice {} — GL not updated, manual posting required",
                    event.getTaxInvoiceId(), e);
        }
    }

    private static VoucherLineDraft line(Long ledgerId, BigDecimal dr, BigDecimal cr, String narration, TaxLineType taxType) {
        VoucherLineDraft l = new VoucherLineDraft();
        l.setLedgerAccountId(ledgerId);
        l.setDrAmount(dr);
        l.setCrAmount(cr);
        l.setNarration(narration);
        l.setTaxType(taxType);
        return l;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
