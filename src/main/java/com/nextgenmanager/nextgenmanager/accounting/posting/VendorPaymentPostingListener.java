package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.service.CoaService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.exception.InvalidVoucherException;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.events.VendorPaymentMadeEvent;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorPayment;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorPaymentRepository;
import com.nextgenmanager.nextgenmanager.sales.model.PaymentMode;
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
 * Auto-posts the PAYMENT voucher when a vendor payment is recorded:
 * <pre>
 *   Dr  Vendor sub-ledger               amount
 *      Cr  Bank / Cash (by payment mode)   amount
 * </pre>
 * Runs AFTER_COMMIT in its own transaction; idempotent on replay.
 */
@Component
@RequiredArgsConstructor
public class VendorPaymentPostingListener {

    private static final Logger log = LoggerFactory.getLogger(VendorPaymentPostingListener.class);
    private static final String SOURCE_DOC_TYPE = SourceDocTypes.VENDOR_PAYMENT;
    private static final String SYSTEM_USER = "SYSTEM";

    private final VendorPaymentRepository paymentRepo;
    private final CoaService coaService;
    private final LedgerResolver ledgers;
    private final PostingService postingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVendorPaymentMade(VendorPaymentMadeEvent event) {
        try {
            VendorPayment payment = paymentRepo.findById(event.getVendorPaymentId()).orElse(null);
            if (payment == null) {
                log.warn("Payment auto-post: vendor payment {} not found — skipped", event.getVendorPaymentId());
                return;
            }
            if (payment.getVendorInvoice() == null || payment.getVendorInvoice().getVendor() == null) {
                log.warn("Payment auto-post: vendor payment {} has no vendor — skipped", payment.getId());
                return;
            }

            Contact vendor = payment.getVendorInvoice().getVendor();
            LedgerAccount party = coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR);
            LedgerAccount cashOrBank = payment.getPaymentMode() == PaymentMode.CASH
                    ? ledgers.cashInHand() : ledgers.bankPrimary();

            BigDecimal amount = nz(payment.getAmount());
            BigDecimal tds = nz(payment.getTdsAmount());
            BigDecimal netBank = amount.subtract(tds);
            String ref = payment.getReferenceNumber() != null ? " (" + payment.getReferenceNumber() + ")" : "";
            String invoiceNo = payment.getVendorInvoice().getInvoiceNumber();

            // Vendor is settled at the gross amount; TDS is withheld so the bank only pays the net.
            List<VoucherLineDraft> lines = new ArrayList<>();
            lines.add(dr(party.getId(), amount, "Against invoice " + invoiceNo, null));
            if (tds.signum() > 0) {
                String section = payment.getTdsSectionCode() != null ? payment.getTdsSectionCode() + " " : "";
                lines.add(cr(ledgers.tdsPayable().getId(), tds,
                        "TDS " + section + "on payment to " + vendor.getCompanyName(), null));
            }
            if (netBank.signum() > 0) {
                lines.add(cr(cashOrBank.getId(), netBank, "Payment to " + vendor.getCompanyName() + ref, null));
            }

            VoucherDraft draft = new VoucherDraft();
            draft.setVoucherType(VoucherType.PAYMENT);
            draft.setDate(payment.getPaymentDate());
            draft.setNarration("Payment " + payment.getPaymentMode() + ref + " to " + vendor.getCompanyName());
            draft.setSourceDocType(SOURCE_DOC_TYPE);
            draft.setSourceDocId(payment.getId());
            draft.setLines(lines);

            VoucherDto voucher = postingService.post(draft, SYSTEM_USER);
            log.info("Auto-posted PAYMENT voucher {} for vendor payment {}", voucher.getVoucherNumber(), payment.getId());

        } catch (InvalidVoucherException e) {
            log.warn("Payment auto-post skipped for vendor payment {}: {}", event.getVendorPaymentId(), e.getMessage());
        } catch (Exception e) {
            log.error("Payment auto-post FAILED for vendor payment {} — GL not updated, manual posting required",
                    event.getVendorPaymentId(), e);
        }
    }
}
