package com.nextgenmanager.nextgenmanager.accounting.tds.listener;

import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntry;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntryStatus;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsSection;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsEntryRepository;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsSectionRepository;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.events.VendorPaymentMadeEvent;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorPayment;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorPaymentRepository;
import com.nextgenmanager.nextgenmanager.sales.model.PaymentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TdsDeductionListenerTest {

    @Mock private VendorPaymentRepository paymentRepo;
    @Mock private TdsSectionRepository sectionRepo;
    @Mock private TdsEntryRepository entryRepo;

    @InjectMocks private TdsDeductionListener listener;

    private VendorPayment payment(BigDecimal amount, BigDecimal tds, String sectionCode) {
        Contact vendor = new Contact();
        vendor.setCompanyName("Contractor Ltd");
        vendor.setPanNumber("ABCDE1234F");
        VendorInvoice inv = new VendorInvoice();
        inv.setInvoiceNumber("VINV-1");
        inv.setVendor(vendor);
        VendorPayment p = new VendorPayment();
        p.setId(50L);
        p.setVendorInvoice(inv);
        p.setPaymentDate(LocalDate.of(2025, 6, 9));
        p.setAmount(amount);
        p.setPaymentMode(PaymentMode.NEFT);
        p.setTdsAmount(tds);
        p.setTdsSectionCode(sectionCode);
        p.setTdsRate(new BigDecimal("2.000"));
        return p;
    }

    private TdsSection section() {
        TdsSection s = new TdsSection();
        s.setId(1L);
        s.setSection("194C");
        s.setRate(new BigDecimal("2.000"));
        return s;
    }

    @Test
    void tdsPayment_recordsDeducteeEntry() {
        VendorPayment p = payment(new BigDecimal("100000.00"), new BigDecimal("2000.00"), "194C");
        when(paymentRepo.findById(50L)).thenReturn(Optional.of(p));
        when(entryRepo.findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull("VENDOR_PAYMENT", 50L))
                .thenReturn(Optional.empty());
        when(sectionRepo.findBySectionAndDeletedDateIsNull("194C")).thenReturn(Optional.of(section()));

        listener.onVendorPaymentMade(new VendorPaymentMadeEvent(50L));

        ArgumentCaptor<TdsEntry> cap = ArgumentCaptor.forClass(TdsEntry.class);
        verify(entryRepo).save(cap.capture());
        TdsEntry e = cap.getValue();
        assertThat(e.getTdsAmount()).isEqualByComparingTo("2000.00");
        assertThat(e.getTaxableAmount()).isEqualByComparingTo("100000.00");
        assertThat(e.getFinancialYear()).isEqualTo("2025-26");
        assertThat(e.getQuarter()).isEqualTo("Q1");
        assertThat(e.getStatus()).isEqualTo(TdsEntryStatus.DEDUCTED);
    }

    @Test
    void noTds_doesNotRecordEntry() {
        VendorPayment p = payment(new BigDecimal("100000.00"), BigDecimal.ZERO, null);
        when(paymentRepo.findById(50L)).thenReturn(Optional.of(p));

        listener.onVendorPaymentMade(new VendorPaymentMadeEvent(50L));

        verify(entryRepo, never()).save(any());
    }

    @Test
    void existingEntry_isIdempotent() {
        VendorPayment p = payment(new BigDecimal("100000.00"), new BigDecimal("2000.00"), "194C");
        when(paymentRepo.findById(50L)).thenReturn(Optional.of(p));
        when(entryRepo.findBySourceDocTypeAndSourceDocIdAndDeletedDateIsNull("VENDOR_PAYMENT", 50L))
                .thenReturn(Optional.of(new TdsEntry()));

        listener.onVendorPaymentMade(new VendorPaymentMadeEvent(50L));

        verify(entryRepo, never()).save(any());
    }
}
