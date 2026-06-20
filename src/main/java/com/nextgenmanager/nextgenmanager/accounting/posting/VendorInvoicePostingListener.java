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
import com.nextgenmanager.nextgenmanager.purchase.events.VendorInvoicePostedEvent;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.cr;
import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.dr;
import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.nz;

/**
 * Auto-posts the PURCHASE voucher when a vendor invoice is posted:
 * <pre>
 *   Dr  GR/IR Clearing  (goods bill, grn != null)   taxable
 *       or  Purchases - Raw Material  (service/expense bill, no grn)
 *   Dr  Input CGST/SGST/IGST/Cess       respective tax amounts
 *      Cr  Vendor sub-ledger              grandTotal
 *      Cr/Dr  Round-off                   balancing difference
 * </pre>
 * Under perpetual inventory (Phase 3) goods received against a GRN are already capitalised to
 * stock (Dr Raw Material / Cr GR/IR) at receipt; the bill therefore clears GR/IR instead of
 * expensing Purchases. Bills with no GRN (services, expenses) keep the direct expense posting.
 */
@Component
@RequiredArgsConstructor
public class VendorInvoicePostingListener {

    private static final Logger log = LoggerFactory.getLogger(VendorInvoicePostingListener.class);
    private static final String SOURCE_DOC_TYPE = SourceDocTypes.VENDOR_INVOICE;
    private static final String SYSTEM_USER = "SYSTEM";

    private final VendorInvoiceRepository invoiceRepo;
    private final CoaService coaService;
    private final LedgerResolver ledgers;
    private final PostingService postingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVendorInvoicePosted(VendorInvoicePostedEvent event) {
        try {
            VendorInvoice inv = invoiceRepo.findByIdAndDeletedDateIsNull(event.getVendorInvoiceId()).orElse(null);
            if (inv == null) {
                log.warn("Purchase auto-post: vendor invoice {} not found — skipped", event.getVendorInvoiceId());
                return;
            }
            if (inv.getVendor() == null) {
                log.warn("Purchase auto-post: invoice {} has no vendor — skipped", inv.getInvoiceNumber());
                return;
            }

            Contact vendor = inv.getVendor();
            LedgerAccount party = coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR);

            BigDecimal cgst = nz(inv.getCgstAmount());
            BigDecimal sgst = nz(inv.getSgstAmount());
            BigDecimal igst = nz(inv.getIgstAmount());
            BigDecimal cess = nz(inv.getCessAmount());
            BigDecimal grandTotal = nz(inv.getGrandTotal());

            // Purchases (expense) = the taxable base that ties to the invoice = grandTotal − input taxes.
            // The header `subtotal` is the GROSS/pre-discount figure and must NOT be used here — doing so
            // leaks the discount into round-off. This derivation balances against Cr Vendor by construction.
            BigDecimal taxable = grandTotal.subtract(cgst).subtract(sgst).subtract(igst).subtract(cess);

            // Perpetual inventory: goods received against a GRN were already capitalised to stock
            // (Dr Raw Material / Cr GR/IR) — clear GR/IR here. Bills without a GRN expense directly.
            boolean goodsBill = inv.getGrn() != null;
            LedgerAccount debitLedger = goodsBill ? ledgers.grIrClearing() : ledgers.purchasesRawMaterial();
            String debitNarration = (goodsBill ? "GR/IR clearing " : "Purchase ") + inv.getInvoiceNumber();

            List<VoucherLineDraft> lines = new ArrayList<>();
            lines.add(dr(debitLedger.getId(), taxable, debitNarration, null));
            if (cgst.signum() > 0) lines.add(dr(ledgers.inputCgst().getId(), cgst, "Input CGST", TaxLineType.CGST));
            if (sgst.signum() > 0) lines.add(dr(ledgers.inputSgst().getId(), sgst, "Input SGST", TaxLineType.SGST));
            if (igst.signum() > 0) lines.add(dr(ledgers.inputIgst().getId(), igst, "Input IGST", TaxLineType.IGST));
            if (cess.signum() > 0) lines.add(dr(ledgers.inputCess().getId(), cess, "Input Cess", TaxLineType.CESS));
            lines.add(cr(party.getId(), grandTotal, "Being vendor invoice " + inv.getInvoiceNumber(), null));

            PostingSupport.balanceWithRoundOff(lines, ledgers);

            VoucherDraft draft = new VoucherDraft();
            draft.setVoucherType(VoucherType.PURCHASE);
            draft.setDate(inv.getInvoiceDate());
            draft.setNarration("Vendor Invoice " + inv.getInvoiceNumber() + " - " + vendor.getCompanyName());
            draft.setSourceDocType(SOURCE_DOC_TYPE);
            draft.setSourceDocId(inv.getId());
            draft.setLines(lines);

            VoucherDto voucher = postingService.post(draft, SYSTEM_USER);
            log.info("Auto-posted PURCHASE voucher {} for vendor invoice {}", voucher.getVoucherNumber(), inv.getInvoiceNumber());

        } catch (InvalidVoucherException e) {
            log.warn("Purchase auto-post skipped for vendor invoice {}: {}", event.getVendorInvoiceId(), e.getMessage());
        } catch (Exception e) {
            log.error("Purchase auto-post FAILED for vendor invoice {} — GL not updated, manual posting required",
                    event.getVendorInvoiceId(), e);
        }
    }
}
