package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.service.CoaService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorPaymentPostingListenerTest {

    @Mock private VendorPaymentRepository paymentRepo;
    @Mock private CoaService coaService;
    @Mock private LedgerResolver ledgers;
    @Mock private PostingService postingService;

    @InjectMocks private VendorPaymentPostingListener listener;

    private LedgerAccount ledger(long id) {
        LedgerAccount la = mock(LedgerAccount.class);
        lenient().when(la.getId()).thenReturn(id);
        return la;
    }

    private VendorPayment payment(PaymentMode mode, BigDecimal amount, Contact vendor) {
        VendorInvoice inv = new VendorInvoice();
        inv.setInvoiceNumber("VINV-1");
        inv.setVendor(vendor);
        VendorPayment p = new VendorPayment();
        p.setId(95L);
        p.setVendorInvoice(inv);
        p.setPaymentDate(LocalDate.of(2025, 6, 9));
        p.setAmount(amount);
        p.setPaymentMode(mode);
        p.setReferenceNumber("NEFT-77");
        return p;
    }

    @Test
    void bankPayment_debitsVendor_creditsBank() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Supplier Ltd");
        VendorPayment p = payment(PaymentMode.NEFT, new BigDecimal("900.00"), vendor);

        LedgerAccount bank = ledger(4011L), party = ledger(8001L);
        when(paymentRepo.findById(95L)).thenReturn(Optional.of(p));
        when(coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR)).thenReturn(party);
        when(ledgers.bankPrimary()).thenReturn(bank);

        listener.onVendorPaymentMade(new VendorPaymentMadeEvent(95L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getVoucherType()).isEqualTo(VoucherType.PAYMENT);
        assertThat(d.getSourceDocType()).isEqualTo("VENDOR_PAYMENT");
        assertThat(d.getLines()).hasSize(2);
        VoucherLineDraft vendorLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(8001L)).findFirst().orElseThrow();
        assertThat(vendorLine.getDrAmount()).isEqualByComparingTo("900.00");
        VoucherLineDraft bankLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(4011L)).findFirst().orElseThrow();
        assertThat(bankLine.getCrAmount()).isEqualByComparingTo("900.00");
    }

    @Test
    void cashPayment_creditsCash() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Cash Vendor");
        VendorPayment p = payment(PaymentMode.CASH, new BigDecimal("250.00"), vendor);

        LedgerAccount cash = ledger(4010L), party = ledger(8001L);
        when(paymentRepo.findById(95L)).thenReturn(Optional.of(p));
        when(coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR)).thenReturn(party);
        when(ledgers.cashInHand()).thenReturn(cash);

        listener.onVendorPaymentMade(new VendorPaymentMadeEvent(95L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getLines()).anyMatch(l -> l.getLedgerAccountId().equals(4010L) &&
                l.getCrAmount().compareTo(new BigDecimal("250.00")) == 0);
    }

    @Test
    void tdsPayment_withholdsTds_creditsTdsPayableAndNetBank() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Contractor Ltd");
        VendorPayment p = payment(PaymentMode.NEFT, new BigDecimal("100000.00"), vendor);
        p.setTdsSectionCode("194C");
        p.setTdsRate(new BigDecimal("2.000"));
        p.setTdsAmount(new BigDecimal("2000.00"));

        LedgerAccount bank = ledger(4011L), party = ledger(8001L), tds = ledger(9015L);
        when(paymentRepo.findById(95L)).thenReturn(Optional.of(p));
        when(coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR)).thenReturn(party);
        when(ledgers.bankPrimary()).thenReturn(bank);
        when(ledgers.tdsPayable()).thenReturn(tds);

        listener.onVendorPaymentMade(new VendorPaymentMadeEvent(95L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getLines()).hasSize(3);
        // Vendor settled at gross
        VoucherLineDraft vendorLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(8001L)).findFirst().orElseThrow();
        assertThat(vendorLine.getDrAmount()).isEqualByComparingTo("100000.00");
        // TDS withheld
        VoucherLineDraft tdsLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(9015L)).findFirst().orElseThrow();
        assertThat(tdsLine.getCrAmount()).isEqualByComparingTo("2000.00");
        // Bank pays the net
        VoucherLineDraft bankLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(4011L)).findFirst().orElseThrow();
        assertThat(bankLine.getCrAmount()).isEqualByComparingTo("98000.00");
    }
}
