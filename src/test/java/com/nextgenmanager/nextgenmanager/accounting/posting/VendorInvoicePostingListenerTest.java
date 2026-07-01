package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.service.CoaService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.events.VendorInvoicePostedEvent;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorInvoiceRepository;
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
class VendorInvoicePostingListenerTest {

    @Mock private VendorInvoiceRepository invoiceRepo;
    @Mock private CoaService coaService;
    @Mock private LedgerResolver ledgers;
    @Mock private PostingService postingService;

    @InjectMocks private VendorInvoicePostingListener listener;

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

    @Test
    void intraStatePurchase_debitsPurchasesAndInputGst_creditsVendor() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Supplier Ltd");
        VendorInvoice inv = new VendorInvoice();
        inv.setId(80L);
        inv.setInvoiceNumber("VINV-1");
        inv.setInvoiceDate(LocalDate.of(2025, 6, 3));
        inv.setVendor(vendor);
        inv.setSubtotal(new BigDecimal("1000.00"));
        inv.setCgstAmount(new BigDecimal("90.00"));
        inv.setSgstAmount(new BigDecimal("90.00"));
        inv.setIgstAmount(BigDecimal.ZERO);
        inv.setCessAmount(BigDecimal.ZERO);
        inv.setGrandTotal(new BigDecimal("1180.00"));

        LedgerAccount party = ledger(8001L), purchases = ledger(5010L), cgst = ledger(6020L), sgst = ledger(6021L);
        when(invoiceRepo.findByIdAndDeletedDateIsNull(80L)).thenReturn(Optional.of(inv));
        when(coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR)).thenReturn(party);
        when(ledgers.purchasesRawMaterial()).thenReturn(purchases);
        when(ledgers.inputCgst()).thenReturn(cgst);
        when(ledgers.inputSgst()).thenReturn(sgst);

        listener.onVendorInvoicePosted(new VendorInvoicePostedEvent(80L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        assertThat(d.getVoucherType()).isEqualTo(VoucherType.PURCHASE);
        assertThat(d.getSourceDocType()).isEqualTo("VENDOR_INVOICE");
        assertThat(d.getLines()).hasSize(4); // purchases, cgst, sgst, vendor (no round-off)
        assertThat(sumDr(d)).isEqualByComparingTo("1180.00");
        assertThat(sumCr(d)).isEqualByComparingTo("1180.00");
        VoucherLineDraft vendorLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(8001L)).findFirst().orElseThrow();
        assertThat(vendorLine.getCrAmount()).isEqualByComparingTo("1180.00");
    }

    /**
     * Regression: header `subtotal` is the GROSS/pre-discount figure and must NOT drive the
     * Purchases debit. Purchases = grandTotal − taxes (the real taxable base), so a discount
     * never leaks into round-off. Mirrors the real bug: subtotal 1800, taxes 291.60, total 1912.
     */
    @Test
    void grossSubtotalWithDiscount_debitsRealTaxable_noRoundOff() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Supplier Ltd");
        VendorInvoice inv = new VendorInvoice();
        inv.setId(80L);
        inv.setInvoiceNumber("IGN22024");
        inv.setInvoiceDate(LocalDate.of(2025, 6, 3));
        inv.setVendor(vendor);
        inv.setSubtotal(new BigDecimal("1800.00"));   // gross, before a 179.60 discount — must be ignored
        inv.setCgstAmount(new BigDecimal("145.80"));
        inv.setSgstAmount(new BigDecimal("145.80"));
        inv.setIgstAmount(BigDecimal.ZERO);
        inv.setCessAmount(BigDecimal.ZERO);
        inv.setGrandTotal(new BigDecimal("1912.00"));

        LedgerAccount party = ledger(8001L), purchases = ledger(5010L), cgst = ledger(6020L), sgst = ledger(6021L);
        when(invoiceRepo.findByIdAndDeletedDateIsNull(80L)).thenReturn(Optional.of(inv));
        when(coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR)).thenReturn(party);
        when(ledgers.purchasesRawMaterial()).thenReturn(purchases);
        when(ledgers.inputCgst()).thenReturn(cgst);
        when(ledgers.inputSgst()).thenReturn(sgst);

        listener.onVendorInvoicePosted(new VendorInvoicePostedEvent(80L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        // Purchases = 1912.00 − 291.60 = 1620.40, NOT 1800.00
        VoucherLineDraft purchasesLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(5010L)).findFirst().orElseThrow();
        assertThat(purchasesLine.getDrAmount()).isEqualByComparingTo("1620.40");
        // No spurious round-off line
        assertThat(d.getLines()).hasSize(4); // purchases, cgst, sgst, vendor
        assertThat(sumDr(d)).isEqualByComparingTo("1912.00");
        assertThat(sumCr(d)).isEqualByComparingTo("1912.00");
    }

    /**
     * Perpetual inventory (Phase 3): a goods bill linked to a GRN clears GR/IR Clearing (6030)
     * — the stock was already capitalised at receipt — instead of expensing Purchases (5010).
     */
    @Test
    void goodsBillWithGrn_debitsGrIrClearing_notPurchases() {
        Contact vendor = new Contact();
        vendor.setCompanyName("Supplier Ltd");
        VendorInvoice inv = new VendorInvoice();
        inv.setId(81L);
        inv.setInvoiceNumber("VINV-2");
        inv.setInvoiceDate(LocalDate.of(2025, 6, 3));
        inv.setVendor(vendor);
        inv.setGrn(new GoodsReceiptNote());           // linked GRN → clear GR/IR, not Purchases
        inv.setSubtotal(new BigDecimal("1000.00"));
        inv.setCgstAmount(new BigDecimal("90.00"));
        inv.setSgstAmount(new BigDecimal("90.00"));
        inv.setIgstAmount(BigDecimal.ZERO);
        inv.setCessAmount(BigDecimal.ZERO);
        inv.setGrandTotal(new BigDecimal("1180.00"));

        LedgerAccount party = ledger(8001L), grIr = ledger(6030L), cgst = ledger(6020L), sgst = ledger(6021L);
        when(invoiceRepo.findByIdAndDeletedDateIsNull(81L)).thenReturn(Optional.of(inv));
        when(coaService.getOrCreatePartyLedger(vendor, SubLedgerType.VENDOR)).thenReturn(party);
        when(ledgers.grIrClearing()).thenReturn(grIr);
        when(ledgers.inputCgst()).thenReturn(cgst);
        when(ledgers.inputSgst()).thenReturn(sgst);

        listener.onVendorInvoicePosted(new VendorInvoicePostedEvent(81L));

        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        VoucherDraft d = cap.getValue();

        // Debit goes to GR/IR Clearing (6030), not Purchases (5010)
        VoucherLineDraft grIrLine = d.getLines().stream()
                .filter(l -> l.getLedgerAccountId().equals(6030L)).findFirst().orElseThrow();
        assertThat(grIrLine.getDrAmount()).isEqualByComparingTo("1000.00");
        assertThat(d.getLines()).noneMatch(l -> l.getLedgerAccountId().equals(5010L));
        assertThat(sumDr(d)).isEqualByComparingTo("1180.00");
        assertThat(sumCr(d)).isEqualByComparingTo("1180.00");
    }
}
