package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.service.CoaService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.events.DebitNoteConfirmedEvent;
import com.nextgenmanager.nextgenmanager.purchase.model.DebitNote;
import com.nextgenmanager.nextgenmanager.purchase.model.GstTreatment;
import com.nextgenmanager.nextgenmanager.purchase.repository.DebitNoteRepository;
import com.nextgenmanager.nextgenmanager.purchase.service.GstResolver;
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
class DebitNotePostingListenerTest {

    @Mock private DebitNoteRepository debitNoteRepo;
    @Mock private CoaService coaService;
    @Mock private LedgerResolver ledgers;
    @Mock private GstResolver gstResolver;
    @Mock private PostingService postingService;

    @InjectMocks private DebitNotePostingListener listener;

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

    private DebitNote debitNote(Contact vendor) {
        DebitNote dn = new DebitNote();
        dn.setId(90L);
        dn.setDebitNoteNumber("DN-1");
        dn.setDebitNoteDate(LocalDate.of(2025, 6, 7));
        dn.setVendor(vendor);
        dn.setSubtotal(1000.0);
        dn.setTotalGstAmount(180.0);
        dn.setTotalAmount(1180.0);
        return dn;
    }

    @Test
    void intraStateReturn_creditsInputCgstSgst() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Supplier Ltd");
        DebitNote dn = debitNote(vendor);

        LedgerAccount party = ledger(8001L), grIr = ledger(6030L), cgst = ledger(6020L), sgst = ledger(6021L);
        when(debitNoteRepo.findByIdAndDeletedDateIsNull(90L)).thenReturn(Optional.of(dn));
        when(coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR)).thenReturn(party);
        when(gstResolver.deriveGstTreatment(vendor)).thenReturn(GstTreatment.INTRA_STATE);
        when(ledgers.grIrClearing()).thenReturn(grIr);
        when(ledgers.inputCgst()).thenReturn(cgst);
        when(ledgers.inputSgst()).thenReturn(sgst);

        listener.onDebitNoteConfirmed(new DebitNoteConfirmedEvent(90L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getVoucherType()).isEqualTo(VoucherType.DEBIT_NOTE);
        assertThat(d.getSourceDocType()).isEqualTo("DEBIT_NOTE");
        assertThat(d.getLines()).hasSize(4); // vendor, grIr, cgst, sgst
        assertThat(sumDr(d)).isEqualByComparingTo("1180.00");
        assertThat(sumCr(d)).isEqualByComparingTo("1180.00");
        // Vendor is debited (they owe us back)
        VoucherLineDraft vendorLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(8001L)).findFirst().orElseThrow();
        assertThat(vendorLine.getDrAmount()).isEqualByComparingTo("1180.00");
        // GR-IR cleared (not Purchases), GST split 90/90
        assertThat(d.getLines()).anyMatch(l -> l.getLedgerAccountId().equals(6030L) &&
                l.getCrAmount().compareTo(new BigDecimal("1000.00")) == 0);
        assertThat(d.getLines()).anyMatch(l -> l.getLedgerAccountId().equals(6020L) &&
                l.getCrAmount().compareTo(new BigDecimal("90.00")) == 0);
    }

    @Test
    void interStateReturn_creditsInputIgst() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Outstate Supplier");
        DebitNote dn = debitNote(vendor);

        LedgerAccount party = ledger(8001L), grIr = ledger(6030L), igst = ledger(6022L);
        when(debitNoteRepo.findByIdAndDeletedDateIsNull(90L)).thenReturn(Optional.of(dn));
        when(coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR)).thenReturn(party);
        when(gstResolver.deriveGstTreatment(vendor)).thenReturn(GstTreatment.INTER_STATE);
        when(ledgers.grIrClearing()).thenReturn(grIr);
        when(ledgers.inputIgst()).thenReturn(igst);

        listener.onDebitNoteConfirmed(new DebitNoteConfirmedEvent(90L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getLines()).hasSize(3); // vendor, purchases, igst
        assertThat(sumDr(d)).isEqualByComparingTo(sumCr(d));
        assertThat(d.getLines()).anyMatch(l -> l.getLedgerAccountId().equals(6022L) &&
                l.getCrAmount().compareTo(new BigDecimal("180.00")) == 0);
    }
}
