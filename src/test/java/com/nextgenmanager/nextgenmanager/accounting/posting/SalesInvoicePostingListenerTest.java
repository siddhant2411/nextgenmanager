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
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.sales.events.TaxInvoiceIssuedEvent;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;
import com.nextgenmanager.nextgenmanager.sales.repository.TaxInvoiceRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesInvoicePostingListenerTest {

    @Mock private TaxInvoiceRepository taxInvoiceRepo;
    @Mock private CoaService coaService;
    @Mock private LedgerResolver ledgers;
    @Mock private PostingService postingService;

    @InjectMocks private SalesInvoicePostingListener listener;

    private LedgerAccount ledger(long id) {
        LedgerAccount la = mock(LedgerAccount.class);
        lenient().when(la.getId()).thenReturn(id);
        return la;
    }

    private TaxInvoice invoice(BigDecimal taxable, BigDecimal cgst, BigDecimal sgst,
                               BigDecimal igst, BigDecimal total, Contact customer) {
        SalesOrder so = new SalesOrder();
        so.setCustomer(customer);
        so.setOrderNumber("SO-1");
        TaxInvoice inv = new TaxInvoice();
        inv.setId(50L);
        inv.setInvoiceNumber("INV-1");
        inv.setInvoiceDate(LocalDate.of(2025, 6, 1));
        inv.setSalesOrder(so);
        inv.setTaxableValue(taxable);
        inv.setCgstAmount(cgst);
        inv.setSgstAmount(sgst);
        inv.setIgstAmount(igst);
        inv.setTotalPayableAmount(total);
        return inv;
    }

    private BigDecimal sumDr(VoucherDraft d) {
        return d.getLines().stream().map(VoucherLineDraft::getDrAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumCr(VoucherDraft d) {
        return d.getLines().stream().map(VoucherLineDraft::getCrAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void autoPosts_balancedSalesVoucher_intraState() {
        Contact customer = new Contact();
        customer.setCompanyName("Acme Pvt Ltd");
        TaxInvoice inv = invoice(new BigDecimal("1000.00"), new BigDecimal("90.00"),
                new BigDecimal("90.00"), BigDecimal.ZERO, new BigDecimal("1180.00"), customer);

        LedgerAccount party = ledger(3001L), sales = ledger(4010L), cgstLed = ledger(9010L), sgstLed = ledger(9011L);
        when(taxInvoiceRepo.findByIdAndDeletedDateIsNull(50L)).thenReturn(Optional.of(inv));
        when(coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER)).thenReturn(party);
        when(ledgers.salesDomestic()).thenReturn(sales);
        when(ledgers.outputCgst()).thenReturn(cgstLed);
        when(ledgers.outputSgst()).thenReturn(sgstLed);

        listener.onTaxInvoiceIssued(new TaxInvoiceIssuedEvent(50L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getVoucherType()).isEqualTo(VoucherType.SALES);
        assertThat(d.getSourceDocType()).isEqualTo("TAX_INVOICE");
        assertThat(d.getSourceDocId()).isEqualTo(50L);
        assertThat(d.getLines()).hasSize(4); // customer, sales, cgst, sgst (no round-off)
        assertThat(sumDr(d)).isEqualByComparingTo("1180.00");
        assertThat(sumCr(d)).isEqualByComparingTo("1180.00");

        // Customer is debited the full payable
        VoucherLineDraft customerLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(3001L)).findFirst().orElseThrow();
        assertThat(customerLine.getDrAmount()).isEqualByComparingTo("1180.00");
        // GST tagged for the register
        assertThat(d.getLines()).anyMatch(l -> l.getTaxType() == TaxLineType.CGST);
        assertThat(d.getLines()).anyMatch(l -> l.getTaxType() == TaxLineType.SGST);
    }

    @Test
    void autoPosts_interState_usesIgstNotCgstSgst() {
        Contact customer = new Contact();
        customer.setCompanyName("Beta Exports");
        TaxInvoice inv = invoice(new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("180.00"), new BigDecimal("1180.00"), customer);

        LedgerAccount party = ledger(3001L), sales = ledger(4010L), igstLed = ledger(9012L);
        when(taxInvoiceRepo.findByIdAndDeletedDateIsNull(50L)).thenReturn(Optional.of(inv));
        when(coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER)).thenReturn(party);
        when(ledgers.salesDomestic()).thenReturn(sales);
        when(ledgers.outputIgst()).thenReturn(igstLed);

        listener.onTaxInvoiceIssued(new TaxInvoiceIssuedEvent(50L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getLines()).hasSize(3); // customer, sales, igst
        assertThat(sumDr(d)).isEqualByComparingTo(sumCr(d));
        assertThat(d.getLines()).anyMatch(l -> l.getTaxType() == TaxLineType.IGST);
        assertThat(d.getLines()).noneMatch(l -> l.getTaxType() == TaxLineType.CGST);
    }

    @Test
    void addsRoundOffLine_whenTotalExceedsTaxablePlusTax() {
        Contact customer = new Contact();
        customer.setCompanyName("Gamma Co");
        // taxable+tax = 1179.60, total billed = 1180.00 → 0.40 round-off income
        TaxInvoice inv = invoice(new BigDecimal("999.60"), new BigDecimal("90.00"),
                new BigDecimal("90.00"), BigDecimal.ZERO, new BigDecimal("1180.00"), customer);

        LedgerAccount party = ledger(3001L), sales = ledger(4010L), cgstLed = ledger(9010L),
                sgstLed = ledger(9011L), roundOffLed = ledger(4023L);
        when(taxInvoiceRepo.findByIdAndDeletedDateIsNull(50L)).thenReturn(Optional.of(inv));
        when(coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER)).thenReturn(party);
        when(ledgers.salesDomestic()).thenReturn(sales);
        when(ledgers.outputCgst()).thenReturn(cgstLed);
        when(ledgers.outputSgst()).thenReturn(sgstLed);
        when(ledgers.roundOffIncome()).thenReturn(roundOffLed);

        listener.onTaxInvoiceIssued(new TaxInvoiceIssuedEvent(50L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(sumDr(d)).isEqualByComparingTo("1180.00");
        assertThat(sumCr(d)).isEqualByComparingTo("1180.00");
        VoucherLineDraft roundOff = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(4023L)).findFirst().orElseThrow();
        assertThat(roundOff.getCrAmount()).isEqualByComparingTo("0.40");
    }

    @Test
    void swallowsIdempotencyConflict_whenVoucherAlreadyExists() {
        Contact customer = new Contact();
        customer.setCompanyName("Delta Ltd");
        TaxInvoice inv = invoice(new BigDecimal("1000.00"), new BigDecimal("90.00"),
                new BigDecimal("90.00"), BigDecimal.ZERO, new BigDecimal("1180.00"), customer);

        LedgerAccount party = ledger(3001L), sales = ledger(4010L), cgstLed = ledger(9010L), sgstLed = ledger(9011L);
        when(taxInvoiceRepo.findByIdAndDeletedDateIsNull(50L)).thenReturn(Optional.of(inv));
        when(coaService.getOrCreatePartyLedger(customer, SubLedgerType.CUSTOMER)).thenReturn(party);
        when(ledgers.salesDomestic()).thenReturn(sales);
        when(ledgers.outputCgst()).thenReturn(cgstLed);
        when(ledgers.outputSgst()).thenReturn(sgstLed);
        when(postingService.post(any(VoucherDraft.class), eq("SYSTEM")))
                .thenThrow(new InvalidVoucherException("A voucher already exists for TAX_INVOICE#50"));

        // Must not propagate — replayed event is a benign no-op.
        listener.onTaxInvoiceIssued(new TaxInvoiceIssuedEvent(50L));
    }

    @Test
    void skips_whenInvoiceNotFound() {
        when(taxInvoiceRepo.findByIdAndDeletedDateIsNull(50L)).thenReturn(Optional.empty());

        listener.onTaxInvoiceIssued(new TaxInvoiceIssuedEvent(50L));

        verify(postingService, never()).post(any(), any());
    }
}
