package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.service.CoaService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.sales.events.SalesPaymentReceivedEvent;
import com.nextgenmanager.nextgenmanager.sales.model.PaymentMode;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesPayment;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesPaymentRepository;
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
class SalesReceiptPostingListenerTest {

    @Mock private SalesPaymentRepository paymentRepo;
    @Mock private CoaService coaService;
    @Mock private LedgerResolver ledgers;
    @Mock private PostingService postingService;

    @InjectMocks private SalesReceiptPostingListener listener;

    private LedgerAccount ledger(long id) {
        LedgerAccount la = mock(LedgerAccount.class);
        lenient().when(la.getId()).thenReturn(id);
        return la;
    }

    private SalesPayment payment(PaymentMode mode, BigDecimal amount, Contact customer) {
        SalesOrder so = new SalesOrder();
        so.setCustomer(customer);
        so.setOrderNumber("SO-1");
        SalesPayment p = new SalesPayment();
        p.setId(70L);
        p.setSalesOrder(so);
        p.setPaymentDate(LocalDate.of(2025, 6, 5));
        p.setAmount(amount);
        p.setPaymentMode(mode);
        p.setReferenceNumber("UTR123");
        return p;
    }

    @Test
    void cashReceipt_debitsCash_creditsCustomer() {
        Contact customer = new Contact();
        customer.setCompanyName("Acme Pvt Ltd");
        SalesPayment p = payment(PaymentMode.CASH, new BigDecimal("500.00"), customer);

        LedgerAccount cash = ledger(4010L), party = ledger(3001L);
        when(paymentRepo.findById(70L)).thenReturn(Optional.of(p));
        when(coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER)).thenReturn(party);
        when(ledgers.cashInHand()).thenReturn(cash);

        listener.onSalesPaymentReceived(new SalesPaymentReceivedEvent(70L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getVoucherType()).isEqualTo(VoucherType.RECEIPT);
        assertThat(d.getSourceDocType()).isEqualTo("SALES_PAYMENT");
        assertThat(d.getLines()).hasSize(2);
        VoucherLineDraft cashLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(4010L)).findFirst().orElseThrow();
        assertThat(cashLine.getDrAmount()).isEqualByComparingTo("500.00");
        VoucherLineDraft custLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(3001L)).findFirst().orElseThrow();
        assertThat(custLine.getCrAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void nonCashReceipt_debitsBank() {
        Contact customer = new Contact();
        customer.setCompanyName("Beta Co");
        SalesPayment p = payment(PaymentMode.NEFT, new BigDecimal("1200.00"), customer);

        LedgerAccount bank = ledger(4011L), party = ledger(3001L);
        when(paymentRepo.findById(70L)).thenReturn(Optional.of(p));
        when(coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER)).thenReturn(party);
        when(ledgers.bankPrimary()).thenReturn(bank);

        listener.onSalesPaymentReceived(new SalesPaymentReceivedEvent(70L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getLines()).anyMatch(l -> l.getLedgerAccountId().equals(4011L) &&
                l.getDrAmount().compareTo(new BigDecimal("1200.00")) == 0);
    }
}
