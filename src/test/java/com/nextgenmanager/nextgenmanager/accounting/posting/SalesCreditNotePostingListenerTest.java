package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.service.CoaService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import com.nextgenmanager.nextgenmanager.purchase.service.GstResolver;
import com.nextgenmanager.nextgenmanager.sales.events.SalesCreditNoteConfirmedEvent;
import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNote;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesCreditNoteRepository;
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
class SalesCreditNotePostingListenerTest {

    @Mock private SalesCreditNoteRepository creditNoteRepo;
    @Mock private CoaService coaService;
    @Mock private LedgerResolver ledgers;
    @Mock private GstResolver gstResolver;
    @Mock private PostingService postingService;

    @InjectMocks private SalesCreditNotePostingListener listener;

    private LedgerAccount ledger(long id) {
        LedgerAccount la = mock(LedgerAccount.class);
        lenient().when(la.getId()).thenReturn(id);
        return la;
    }

    private BigDecimal sumDr(VoucherDraft d) {
        return d.getLines().stream().map(VoucherLineDraft::getDrAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumCr(VoucherDraft d) {
        return d.getLines().stream().map(VoucherLineDraft::getCrAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SalesCreditNote creditNote(Contact customer) {
        SalesCreditNote cn = new SalesCreditNote();
        cn.setId(100L);
        cn.setCreditNoteNumber("CN-1");
        cn.setCreditNoteDate(LocalDate.of(2025, 6, 11));
        cn.setCustomer(customer);
        cn.setSubtotal(1000.0);
        cn.setTotalGstAmount(180.0);
        cn.setTotalAmount(1180.0);
        return cn;
    }

    @Test
    void intraStateReturn_debitsSalesAndOutputCgstSgst_creditsCustomer() {
        Contact customer = new Contact();
        customer.setCompanyName("Acme Pvt Ltd");
        SalesCreditNote cn = creditNote(customer);

        LedgerAccount party = ledger(3001L), sales = ledger(4010L), cgst = ledger(9010L), sgst = ledger(9011L);
        when(creditNoteRepo.findByIdAndDeletedDateIsNull(100L)).thenReturn(Optional.of(cn));
        when(coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER)).thenReturn(party);
        when(gstResolver.deriveGstTreatment(customer)).thenReturn(GstTreatment.INTRA_STATE);
        when(ledgers.salesDomestic()).thenReturn(sales);
        when(ledgers.outputCgst()).thenReturn(cgst);
        when(ledgers.outputSgst()).thenReturn(sgst);

        listener.onSalesCreditNoteConfirmed(new SalesCreditNoteConfirmedEvent(100L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getVoucherType()).isEqualTo(VoucherType.CREDIT_NOTE);
        assertThat(d.getSourceDocType()).isEqualTo("SALES_CREDIT_NOTE");
        assertThat(d.getLines()).hasSize(4); // sales, cgst, sgst, customer
        assertThat(sumDr(d)).isEqualByComparingTo("1180.00");
        assertThat(sumCr(d)).isEqualByComparingTo("1180.00");
        // Customer is credited (we owe them back)
        VoucherLineDraft customerLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(3001L)).findFirst().orElseThrow();
        assertThat(customerLine.getCrAmount()).isEqualByComparingTo("1180.00");
        // Sales debited the taxable
        VoucherLineDraft salesLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(4010L)).findFirst().orElseThrow();
        assertThat(salesLine.getDrAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void interStateReturn_debitsOutputIgst() {
        Contact customer = new Contact();
        customer.setCompanyName("Outstate Buyer");
        SalesCreditNote cn = creditNote(customer);

        LedgerAccount party = ledger(3001L), sales = ledger(4010L), igst = ledger(9012L);
        when(creditNoteRepo.findByIdAndDeletedDateIsNull(100L)).thenReturn(Optional.of(cn));
        when(coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER)).thenReturn(party);
        when(gstResolver.deriveGstTreatment(customer)).thenReturn(GstTreatment.INTER_STATE);
        when(ledgers.salesDomestic()).thenReturn(sales);
        when(ledgers.outputIgst()).thenReturn(igst);

        listener.onSalesCreditNoteConfirmed(new SalesCreditNoteConfirmedEvent(100L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getLines()).hasSize(3); // sales, igst, customer
        assertThat(sumDr(d)).isEqualByComparingTo(sumCr(d));
        assertThat(d.getLines()).anyMatch(l -> l.getLedgerAccountId().equals(9012L) &&
                l.getDrAmount().compareTo(new BigDecimal("180.00")) == 0);
    }
}
