package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.Inventory.events.InventoryMovementPostedEvent;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryLedger;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
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
class InventoryPostingListenerTest {

    @Mock private InventoryLedgerRepository ledgerRepo;
    @Mock private LedgerResolver ledgers;
    @Mock private PostingService postingService;

    @InjectMocks private InventoryPostingListener listener;

    private final InventoryItem item = mock(InventoryItem.class);

    private LedgerAccount ledger(long id) {
        LedgerAccount la = mock(LedgerAccount.class);
        lenient().when(la.getId()).thenReturn(id);
        return la;
    }

    private InventoryLedger row(long id, String txn, String refType, double qty, double amount) {
        InventoryLedger l = new InventoryLedger();
        l.setId(id);
        l.setTransactionType(txn);
        l.setReferenceType(refType);
        l.setQuantity(qty);
        l.setAmount(amount);
        l.setMovementDate(LocalDate.of(2025, 6, 10));
        l.setInventoryItem(item);
        return l;
    }

    private void stubPost() {
        VoucherDto vd = mock(VoucherDto.class);
        lenient().when(vd.getVoucherNumber()).thenReturn("JNL/2025-26/0001");
        when(postingService.post(any(), eq("SYSTEM"))).thenReturn(vd);
    }

    private VoucherDraft captureDraft() {
        ArgumentCaptor<VoucherDraft> cap = ArgumentCaptor.forClass(VoucherDraft.class);
        verify(postingService).post(cap.capture(), eq("SYSTEM"));
        return cap.getValue();
    }

    private BigDecimal drOf(VoucherDraft d, long ledgerId) {
        return d.getLines().stream().filter(l -> l.getLedgerAccountId().equals(ledgerId))
                .map(VoucherLineDraft::getDrAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal crOf(VoucherDraft d, long ledgerId) {
        return d.getLines().stream().filter(l -> l.getLedgerAccountId().equals(ledgerId))
                .map(VoucherLineDraft::getCrAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void grn_debitsStock_creditsGrIrClearing() {
        LedgerAccount stock = ledger(2010L), grIr = ledger(6030L);
        when(ledgerRepo.findById(1L)).thenReturn(Optional.of(row(1L, "GRN", "GRN", 10, 1000)));
        when(ledgers.stockLedgerFor(item)).thenReturn(stock);
        when(ledgers.grIrClearing()).thenReturn(grIr);
        stubPost();

        listener.onInventoryMovement(new InventoryMovementPostedEvent(1L));

        VoucherDraft d = captureDraft();
        assertThat(d.getVoucherType()).isEqualTo(VoucherType.JOURNAL);
        assertThat(d.getSourceDocType()).isEqualTo("INVENTORY_MOVEMENT");
        assertThat(d.getSourceDocId()).isEqualTo(1L);
        assertThat(drOf(d, 2010L)).isEqualByComparingTo("1000.00");
        assertThat(crOf(d, 6030L)).isEqualByComparingTo("1000.00");
    }

    @Test
    void workOrderConsume_debitsWip_creditsStock() {
        LedgerAccount wip = ledger(2011L), stock = ledger(2010L);
        when(ledgerRepo.findById(2L)).thenReturn(Optional.of(row(2L, "CONSUME", "WORK_ORDER", -4, 400)));
        when(ledgers.wip()).thenReturn(wip);
        when(ledgers.stockLedgerFor(item)).thenReturn(stock);
        stubPost();

        listener.onInventoryMovement(new InventoryMovementPostedEvent(2L));

        VoucherDraft d = captureDraft();
        assertThat(drOf(d, 2011L)).isEqualByComparingTo("400.00");
        assertThat(crOf(d, 2010L)).isEqualByComparingTo("400.00");
    }

    @Test
    void workOrderComplete_debitsFinishedGoods_creditsWip() {
        LedgerAccount fg = ledger(2012L), wip = ledger(2011L);
        when(ledgerRepo.findById(3L)).thenReturn(Optional.of(row(3L, "PRODUCE", "WORK_ORDER", 4, 500)));
        when(ledgers.finishedGoodsStock()).thenReturn(fg);
        when(ledgers.wip()).thenReturn(wip);
        stubPost();

        listener.onInventoryMovement(new InventoryMovementPostedEvent(3L));

        VoucherDraft d = captureDraft();
        assertThat(drOf(d, 2012L)).isEqualByComparingTo("500.00");
        assertThat(crOf(d, 2011L)).isEqualByComparingTo("500.00");
    }

    @Test
    void salesDispatch_debitsCogs_creditsFinishedGoods() {
        LedgerAccount cogs = ledger(5020L), fg = ledger(2012L);
        when(ledgerRepo.findById(4L)).thenReturn(Optional.of(row(4L, "SALES_DISPATCH", "DELIVERY_NOTE", -2, 300)));
        when(ledgers.cogs()).thenReturn(cogs);
        when(ledgers.stockLedgerFor(item)).thenReturn(fg);
        stubPost();

        listener.onInventoryMovement(new InventoryMovementPostedEvent(4L));

        VoucherDraft d = captureDraft();
        assertThat(drOf(d, 5020L)).isEqualByComparingTo("300.00");
        assertThat(crOf(d, 2012L)).isEqualByComparingTo("300.00");
    }

    @Test
    void adjustmentLoss_debitsAdjustment_creditsStock() {
        LedgerAccount adj = ledger(5063L), stock = ledger(2010L);
        when(ledgerRepo.findById(5L)).thenReturn(Optional.of(row(5L, "ADJUSTMENT", null, -3, 150)));
        when(ledgers.inventoryAdjustment()).thenReturn(adj);
        when(ledgers.stockLedgerFor(item)).thenReturn(stock);
        stubPost();

        listener.onInventoryMovement(new InventoryMovementPostedEvent(5L));

        VoucherDraft d = captureDraft();
        assertThat(drOf(d, 5063L)).isEqualByComparingTo("150.00");
        assertThat(crOf(d, 2010L)).isEqualByComparingTo("150.00");
    }

    @Test
    void adjustmentGain_debitsStock_creditsGain() {
        LedgerAccount stock = ledger(2010L), gain = ledger(4024L);
        when(ledgerRepo.findById(6L)).thenReturn(Optional.of(row(6L, "ADJUSTMENT", null, 3, 150)));
        when(ledgers.stockLedgerFor(item)).thenReturn(stock);
        when(ledgers.inventoryAdjustmentGain()).thenReturn(gain);
        stubPost();

        listener.onInventoryMovement(new InventoryMovementPostedEvent(6L));

        VoucherDraft d = captureDraft();
        assertThat(drOf(d, 2010L)).isEqualByComparingTo("150.00");
        assertThat(crOf(d, 4024L)).isEqualByComparingTo("150.00");
    }

    @Test
    void openingStock_debitsStock_creditsOpeningBalanceEquity_notIncome() {
        LedgerAccount stock = ledger(2010L), equity = ledger(3022L);
        // Opening-stock onboarding: transactionType ADJUSTMENT but referenceType OPENING_STOCK.
        when(ledgerRepo.findById(9L)).thenReturn(Optional.of(row(9L, "ADJUSTMENT", "OPENING_STOCK", 100, 25000)));
        when(ledgers.stockLedgerFor(item)).thenReturn(stock);
        when(ledgers.openingBalanceEquity()).thenReturn(equity);
        stubPost();

        listener.onInventoryMovement(new InventoryMovementPostedEvent(9L));

        VoucherDraft d = captureDraft();
        assertThat(drOf(d, 2010L)).isEqualByComparingTo("25000.00");
        assertThat(crOf(d, 3022L)).isEqualByComparingTo("25000.00");
        // must NOT hit Inventory Adjustment Gain (income)
        assertThat(d.getLines()).noneMatch(l -> l.getLedgerAccountId().equals(4024L));
    }

    @Test
    void purchaseReturn_creditsStock_debitsGrIr() {
        LedgerAccount stock = ledger(2010L), grIr = ledger(6030L);
        when(ledgerRepo.findById(10L)).thenReturn(Optional.of(row(10L, "PURCHASE_RETURN", "DEBIT_NOTE", -5, 500)));
        when(ledgers.stockLedgerFor(item)).thenReturn(stock);
        when(ledgers.grIrClearing()).thenReturn(grIr);
        stubPost();

        listener.onInventoryMovement(new InventoryMovementPostedEvent(10L));

        VoucherDraft d = captureDraft();
        assertThat(crOf(d, 2010L)).isEqualByComparingTo("500.00");
        assertThat(drOf(d, 6030L)).isEqualByComparingTo("500.00");
    }

    @Test
    void salesReturn_debitsStock_creditsCogs() {
        LedgerAccount fg = ledger(2012L), cogs = ledger(5020L);
        when(ledgerRepo.findById(11L)).thenReturn(Optional.of(row(11L, "SALES_RETURN", "SALES_CREDIT_NOTE", 3, 600)));
        when(ledgers.stockLedgerFor(item)).thenReturn(fg);
        when(ledgers.cogs()).thenReturn(cogs);
        stubPost();

        listener.onInventoryMovement(new InventoryMovementPostedEvent(11L));

        VoucherDraft d = captureDraft();
        assertThat(drOf(d, 2012L)).isEqualByComparingTo("600.00");
        assertThat(crOf(d, 5020L)).isEqualByComparingTo("600.00");
    }

    @Test
    void reserve_isNotAValueMovement_noVoucher() {
        when(ledgerRepo.findById(7L)).thenReturn(Optional.of(row(7L, "RESERVE", "WORK_ORDER", -5, 500)));

        listener.onInventoryMovement(new InventoryMovementPostedEvent(7L));

        verify(postingService, never()).post(any(), any());
    }

    @Test
    void issueToFloor_zeroValued_noVoucher() {
        when(ledgerRepo.findById(8L)).thenReturn(Optional.of(row(8L, "ISSUE", "WORK_ORDER", 0, 0)));

        listener.onInventoryMovement(new InventoryMovementPostedEvent(8L));

        verify(postingService, never()).post(any(), any());
    }
}
