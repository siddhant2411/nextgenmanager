package com.nextgenmanager.nextgenmanager.accounting.tds.listener;

import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntry;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntryStatus;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsSection;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsEntryRepository;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsSectionRepository;
import com.nextgenmanager.nextgenmanager.accounting.tds.util.TdsPeriods;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.events.VendorPaymentMadeEvent;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorPayment;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

import static com.nextgenmanager.nextgenmanager.accounting.posting.PostingSupport.nz;

/**
 * Mirrors a TDS-bearing vendor payment into a deductee-wise {@link TdsEntry} — the source for the
 * 26Q return. Runs AFTER_COMMIT in its own transaction and is idempotent on (sourceDocType, sourceDocId),
 * so a failure here never rolls back the payment and replays are harmless. The GL credit to TDS Payable
 * is handled separately by {@code VendorPaymentPostingListener}.
 */
@Component
@RequiredArgsConstructor
public class TdsDeductionListener {

    private static final Logger log = LoggerFactory.getLogger(TdsDeductionListener.class);
    private static final String SOURCE_DOC_TYPE = SourceDocTypes.VENDOR_PAYMENT;

    private final VendorPaymentRepository paymentRepo;
    private final TdsSectionRepository sectionRepo;
    private final TdsEntryRepository entryRepo;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVendorPaymentMade(VendorPaymentMadeEvent event) {
        try {
            VendorPayment payment = paymentRepo.findById(event.getVendorPaymentId()).orElse(null);
            if (payment == null) return;

            BigDecimal tds = nz(payment.getTdsAmount());
            if (tds.signum() <= 0 || payment.getTdsSectionCode() == null) return; // no TDS on this payment

            if (entryRepo.findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull(SOURCE_DOC_TYPE, payment.getId()).isPresent()) {
                return; // already recorded
            }

            TdsSection section = sectionRepo.findBySectionAndDeletedDateIsNull(payment.getTdsSectionCode()).orElse(null);
            if (section == null) {
                log.warn("TDS entry skipped for vendor payment {}: section '{}' not found", payment.getId(), payment.getTdsSectionCode());
                return;
            }
            Contact vendor = payment.getVendorInvoice() != null ? payment.getVendorInvoice().getVendor() : null;
            if (vendor == null) {
                log.warn("TDS entry skipped for vendor payment {}: no vendor", payment.getId());
                return;
            }

            TdsEntry entry = new TdsEntry();
            entry.setSection(section);
            entry.setContact(vendor);
            entry.setSourceDocType(SOURCE_DOC_TYPE);
            entry.setSourceDocId(payment.getId());
            entry.setTaxableAmount(nz(payment.getAmount()));
            entry.setTdsAmount(tds);
            entry.setRate(payment.getTdsRate() != null ? payment.getTdsRate() : section.getRate());
            entry.setFinancialYear(TdsPeriods.financialYear(payment.getPaymentDate()));
            entry.setQuarter(TdsPeriods.quarter(payment.getPaymentDate()));
            entry.setDeductionDate(payment.getPaymentDate());
            entry.setStatus(TdsEntryStatus.DEDUCTED);
            entryRepo.save(entry);

            log.info("Recorded TDS entry {} ({} {}) for vendor payment {}",
                    entry.getId(), section.getSection(), tds, payment.getId());

        } catch (Exception e) {
            log.error("TDS entry FAILED for vendor payment {} — deductee record missing, fix manually",
                    event.getVendorPaymentId(), e);
        }
    }
}
