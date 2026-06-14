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
import com.nextgenmanager.nextgenmanager.sales.events.SalesPaymentReceivedEvent;
import com.nextgenmanager.nextgenmanager.sales.model.PaymentMode;
import com.nextgenmanager.nextgenmanager.sales.model.SalesPayment;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesPaymentRepository;
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
 * Auto-posts the RECEIPT voucher for a customer payment:
 * <pre>
 *   Dr  Bank / Cash (by payment mode)   amount
 *      Cr  Customer sub-ledger            amount
 * </pre>
 * Runs AFTER_COMMIT in its own transaction; idempotent on replay.
 */
@Component
@RequiredArgsConstructor
public class SalesReceiptPostingListener {

    private static final Logger log = LoggerFactory.getLogger(SalesReceiptPostingListener.class);
    private static final String SOURCE_DOC_TYPE = SourceDocTypes.SALES_PAYMENT;
    private static final String SYSTEM_USER = "SYSTEM";

    private final SalesPaymentRepository paymentRepo;
    private final CoaService coaService;
    private final LedgerResolver ledgers;
    private final PostingService postingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSalesPaymentReceived(SalesPaymentReceivedEvent event) {
        try {
            SalesPayment payment = paymentRepo.findById(event.getSalesPaymentId()).orElse(null);
            if (payment == null) {
                log.warn("Receipt auto-post: payment {} not found — skipped", event.getSalesPaymentId());
                return;
            }
            if (payment.getSalesOrder() == null || payment.getSalesOrder().getCustomer() == null) {
                log.warn("Receipt auto-post: payment {} has no customer — skipped", payment.getId());
                return;
            }

            Contact customer = payment.getSalesOrder().getCustomer();
            LedgerAccount party = coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER);
            LedgerAccount cashOrBank = payment.getPaymentMode() == PaymentMode.CASH
                    ? ledgers.cashInHand() : ledgers.bankPrimary();

            BigDecimal amount = nz(payment.getAmount());
            String ref = payment.getReferenceNumber() != null ? " (" + payment.getReferenceNumber() + ")" : "";

            List<VoucherLineDraft> lines = new ArrayList<>();
            lines.add(dr(cashOrBank.getId(), amount, "Receipt from " + customer.getCompanyName() + ref, null));
            lines.add(cr(party.getId(), amount, "Against SO " + payment.getSalesOrder().getOrderNumber(), null));

            VoucherDraft draft = new VoucherDraft();
            draft.setVoucherType(VoucherType.RECEIPT);
            draft.setDate(payment.getPaymentDate());
            draft.setNarration("Receipt " + payment.getPaymentMode() + ref + " from " + customer.getCompanyName());
            draft.setSourceDocType(SOURCE_DOC_TYPE);
            draft.setSourceDocId(payment.getId());
            draft.setLines(lines);

            VoucherDto voucher = postingService.post(draft, SYSTEM_USER);
            log.info("Auto-posted RECEIPT voucher {} for payment {}", voucher.getVoucherNumber(), payment.getId());

        } catch (InvalidVoucherException e) {
            log.warn("Receipt auto-post skipped for payment {}: {}", event.getSalesPaymentId(), e.getMessage());
        } catch (Exception e) {
            log.error("Receipt auto-post FAILED for payment {} — GL not updated, manual posting required",
                    event.getSalesPaymentId(), e);
        }
    }
}
