package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.Inventory.events.InventoryMovementPostedEvent;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryLedger;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.exception.InvalidVoucherException;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
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

/**
 * Perpetual-inventory auto-posting (Phase 3). Listens to every valued stock movement
 * ({@link InventoryMovementPostedEvent}, one per {@code InventoryLedger} row) and books the
 * matching GL voucher so the Balance-Sheet stock accounts tie to the inventory ledger.
 *
 * <p>Posting amount is the inventory ledger's own valued {@code amount}, so the GL ties to the
 * stock ledger <em>by construction</em>. Only the value-changing transaction types below are
 * posted; availability/location moves (RESERVE, ISSUE) and zero-valued rows are ignored.
 *
 * <pre>
 *   GRN                       Dr stock(item)            Cr GR/IR Clearing
 *   CONSUME (WORK_ORDER)      Dr WIP                     Cr stock(item)
 *   PRODUCE (WORK_ORDER)      Dr Finished Goods Stock    Cr WIP
 *   SALES_DISPATCH            Dr COGS                    Cr stock(item=FG)
 *   RETURN  (WORK_ORDER)      Dr stock(item)             Cr WIP
 *   ADJUSTMENT (+qty)         Dr stock(item)             Cr Inventory Adjustment Gain
 *   ADJUSTMENT (-qty)         Dr Inventory Adjustment    Cr stock(item)
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class InventoryPostingListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryPostingListener.class);
    private static final String SOURCE_DOC_TYPE = SourceDocTypes.INVENTORY_MOVEMENT;
    private static final String SYSTEM_USER = "SYSTEM";

    // Inventory funnel transactionType / referenceType values (the posting allowlist).
    private static final String TXN_GRN = "GRN";
    private static final String TXN_CONSUME = "CONSUME";
    private static final String TXN_PRODUCE = "PRODUCE";
    private static final String TXN_DISPATCH = "SALES_DISPATCH";
    private static final String TXN_ADJUSTMENT = "ADJUSTMENT";
    private static final String TXN_RETURN = "RETURN";
    private static final String TXN_PURCHASE_RETURN = "PURCHASE_RETURN";
    private static final String TXN_SALES_RETURN = "SALES_RETURN";
    private static final String REF_WORK_ORDER = "WORK_ORDER";
    private static final String REF_OPENING_STOCK = "OPENING_STOCK";

    private final InventoryLedgerRepository ledgerRepo;
    private final LedgerResolver ledgers;
    private final PostingService postingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInventoryMovement(InventoryMovementPostedEvent event) {
        try {
            InventoryLedger row = ledgerRepo.findById(event.getInventoryLedgerId()).orElse(null);
            if (row == null) {
                log.warn("Inventory auto-post: ledger row {} not found — skipped", event.getInventoryLedgerId());
                return;
            }

            BigDecimal amount = PostingSupport.money(Math.abs(row.getAmount()));
            if (amount.signum() == 0) {
                return; // issue-to-floor / zero-valued row — no ownership-value change to post
            }

            VoucherDraft draft = buildDraft(row, amount);
            if (draft == null) {
                return; // transactionType not in the perpetual-inventory allowlist (reserve, issue, …)
            }

            VoucherDto voucher = postingService.post(draft, SYSTEM_USER);
            log.info("Auto-posted inventory voucher {} for {} movement (ledger #{}, amount {})",
                    voucher.getVoucherNumber(), row.getTransactionType(), row.getId(), amount);

        } catch (InvalidVoucherException e) {
            // Benign on replay (idempotency: voucher already exists for this ledger row).
            log.warn("Inventory auto-post skipped for ledger {}: {}", event.getInventoryLedgerId(), e.getMessage());
        } catch (Exception e) {
            log.error("Inventory auto-post FAILED for ledger {} — GL not updated, manual posting required",
                    event.getInventoryLedgerId(), e);
        }
    }

    /**
     * Builds the two-line voucher for a valued movement per the posting contract, or {@code null}
     * when the movement is not a GL event (reserve, issue, or a non-work-order consume/produce/return).
     */
    private VoucherDraft buildDraft(InventoryLedger row, BigDecimal amount) {
        InventoryItem item = row.getInventoryItem();
        String txn = row.getTransactionType();
        boolean fromWorkOrder = REF_WORK_ORDER.equals(row.getReferenceType());
        String ref = row.getReferenceDocNo() != null ? row.getReferenceDocNo() : "";

        List<VoucherLineDraft> lines = new ArrayList<>(2);
        String narration;

        if (TXN_GRN.equals(txn)) {
            lines.add(dr(ledgers.stockLedgerFor(item).getId(), amount, "Goods received " + ref, null));
            lines.add(cr(ledgers.grIrClearing().getId(), amount, "GR/IR clearing " + ref, null));
            narration = "GRN " + ref;
        } else if (TXN_CONSUME.equals(txn) && fromWorkOrder) {
            lines.add(dr(ledgers.wip().getId(), amount, "Material to WIP " + ref, null));
            lines.add(cr(ledgers.stockLedgerFor(item).getId(), amount, "Consumed " + ref, null));
            narration = "WO consume " + ref;
        } else if (TXN_PRODUCE.equals(txn) && fromWorkOrder) {
            // A work order's output is always a finished/semi-finished good, so it lands in Finished
            // Goods Stock regardless of the item's master-data ItemType (which may be mis-set). Trusting
            // stockLedgerFor(item) here mis-routed produce value into Raw Material Stock for mistyped items.
            lines.add(dr(ledgers.finishedGoodsStock().getId(), amount, "Finished goods " + ref, null));
            lines.add(cr(ledgers.wip().getId(), amount, "WIP absorbed " + ref, null));
            narration = "WO complete " + ref;
        } else if (TXN_DISPATCH.equals(txn)) {
            lines.add(dr(ledgers.cogs().getId(), amount, "COGS " + ref, null));
            lines.add(cr(ledgers.stockLedgerFor(item).getId(), amount, "Dispatched " + ref, null));
            narration = "Sales dispatch " + ref;
        } else if (TXN_RETURN.equals(txn) && fromWorkOrder) {
            // Material returned from shop floor to store — reverses the WO consume.
            lines.add(dr(ledgers.stockLedgerFor(item).getId(), amount, "Returned to store " + ref, null));
            lines.add(cr(ledgers.wip().getId(), amount, "WIP reversed " + ref, null));
            narration = "WO return " + ref;
        } else if (TXN_PURCHASE_RETURN.equals(txn)) {
            // Purchase return / debit note — mirror of GRN (Cr stock / Dr GR-IR clearing).
            // The DebitNote voucher then Dr Vendor / Cr GR-IR / Cr Input GST; GR-IR nets to zero.
            lines.add(cr(ledgers.stockLedgerFor(item).getId(), amount, "Purchase return " + ref, null));
            lines.add(dr(ledgers.grIrClearing().getId(), amount, "GR/IR clearing " + ref, null));
            narration = "Purchase return " + ref;
        } else if (TXN_SALES_RETURN.equals(txn)) {
            // Sales return / credit note — reverses the SALES_DISPATCH (Dr stock / Cr COGS).
            // The SalesCreditNote voucher handles Sales / Output-GST / Customer reversal separately.
            lines.add(dr(ledgers.stockLedgerFor(item).getId(), amount, "Sales return " + ref, null));
            lines.add(cr(ledgers.cogs().getId(), amount, "COGS reversed " + ref, null));
            narration = "Sales return " + ref;
        } else if (TXN_ADJUSTMENT.equals(txn)) {
            // Opening stock (migration cutover) credits Opening Balance Equity, not P&L; a regular
            // adjustment hits Inventory Adjustment Gain (income) / Write-off (expense) by direction.
            boolean openingStock = REF_OPENING_STOCK.equals(row.getReferenceType());
            boolean increase = row.getQuantity() >= 0;
            LedgerAccount counter = openingStock ? ledgers.openingBalanceEquity()
                    : (increase ? ledgers.inventoryAdjustmentGain() : ledgers.inventoryAdjustment());
            String label = openingStock ? "Opening stock " : (increase ? "Stock gain " : "Inventory write-off ");
            if (increase) {
                lines.add(dr(ledgers.stockLedgerFor(item).getId(), amount, label + ref, null));
                lines.add(cr(counter.getId(), amount, label + ref, null));
            } else {
                lines.add(dr(counter.getId(), amount, label + ref, null));
                lines.add(cr(ledgers.stockLedgerFor(item).getId(), amount, label + ref, null));
            }
            narration = (openingStock ? "Opening stock " : "Stock adjustment ") + ref;
        } else {
            return null; // RESERVE, ISSUE, non-WO produce/consume — not a GL event
        }

        VoucherDraft draft = new VoucherDraft();
        draft.setVoucherType(VoucherType.JOURNAL);
        draft.setDate(row.getMovementDate());
        draft.setNarration(narration + (item != null ? " — " + item.getItemCode() : ""));
        draft.setSourceDocType(SOURCE_DOC_TYPE);
        draft.setSourceDocId(row.getId());
        draft.setLines(lines);
        return draft;
    }
}
