package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Resolves well-known system ledger accounts by their seeded COA code (V128).
 * Auto-posting maps document amounts onto these fixed accounts ("convention by
 * ledger code"). A missing system ledger is a configuration error and fails loud.
 */
@Service
@RequiredArgsConstructor
public class LedgerResolver {

    // ─ Income ─
    public static final String SALES_DOMESTIC = "4010I";
    public static final String SALES_EXPORT = "4011I";
    public static final String ROUND_OFF_INCOME = "4023I";
    // ─ Output GST (Duties & Taxes Payable) ─
    public static final String OUTPUT_CGST = "9010";
    public static final String OUTPUT_SGST = "9011";
    public static final String OUTPUT_IGST = "9012";
    public static final String OUTPUT_CESS = "9013";
    // ─ Expense ─
    public static final String PURCHASES_RAW_MATERIAL = "5010E";
    public static final String PURCHASES_TRADING_GOODS = "5011E";
    public static final String ROUND_OFF_EXPENSE = "5062E";
    // ─ Input GST (ITC) ─
    public static final String INPUT_CGST = "6020";
    public static final String INPUT_SGST = "6021";
    public static final String INPUT_IGST = "6022";
    public static final String INPUT_CESS = "6023";
    // ─ Cash & Bank ─
    public static final String CASH_IN_HAND = "4010";
    public static final String BANK_PRIMARY = "4011";
    // ─ Control accounts ─
    public static final String SUNDRY_DEBTORS = "3010";
    public static final String SUNDRY_CREDITORS = "8010";
    // ─ Perpetual inventory (Phase 3) ─
    public static final String RAW_MATERIAL_STOCK = "2010";
    public static final String WIP_STOCK = "2011";
    public static final String FINISHED_GOODS_STOCK = "2012";
    public static final String GR_IR_CLEARING = "6030";
    public static final String COGS = "5020E";
    public static final String INVENTORY_ADJUSTMENT = "5063E";       // expense (write-off / loss)
    public static final String INVENTORY_ADJUSTMENT_GAIN = "4024I";  // income (write-up / gain)
    public static final String OPENING_BALANCE_EQUITY = "3022E";     // cutover bridge for opening stock
    // ─ Statutory deductions (Phase 4) ─
    public static final String TDS_PAYABLE = "9015";
    // ─ Payroll (Phase 4) ─
    public static final String SALARIES_WAGES = "5030E";
    public static final String SALARY_PAYABLE = "9031";
    public static final String PF_PAYABLE = "9017";
    public static final String ESI_PAYABLE = "9018";
    public static final String PT_PAYABLE = "9019";
    // ─ Depreciation (Phase 4) ─
    public static final String DEPRECIATION_EXPENSE = "5070E";
    public static final String ACCUMULATED_DEPRECIATION = "1020";

    private final LedgerAccountRepository ledgerRepo;

    public LedgerAccount byCode(String code) {
        return ledgerRepo.findByCodeAndDeletedDateIsNull(code)
                .orElseThrow(() -> new IllegalStateException(
                        "Required system ledger '" + code + "' not found. Is the COA seed (V128) applied?"));
    }

    public LedgerAccount salesDomestic()    { return byCode(SALES_DOMESTIC); }
    public LedgerAccount outputCgst()       { return byCode(OUTPUT_CGST); }
    public LedgerAccount outputSgst()       { return byCode(OUTPUT_SGST); }
    public LedgerAccount outputIgst()       { return byCode(OUTPUT_IGST); }
    public LedgerAccount roundOffIncome()   { return byCode(ROUND_OFF_INCOME); }
    public LedgerAccount roundOffExpense()  { return byCode(ROUND_OFF_EXPENSE); }

    public LedgerAccount purchasesRawMaterial() { return byCode(PURCHASES_RAW_MATERIAL); }
    public LedgerAccount inputCgst()        { return byCode(INPUT_CGST); }
    public LedgerAccount inputSgst()        { return byCode(INPUT_SGST); }
    public LedgerAccount inputIgst()        { return byCode(INPUT_IGST); }
    public LedgerAccount inputCess()        { return byCode(INPUT_CESS); }
    public LedgerAccount cashInHand()       { return byCode(CASH_IN_HAND); }
    public LedgerAccount bankPrimary()      { return byCode(BANK_PRIMARY); }

    // ─ Perpetual inventory (Phase 3) ─
    public LedgerAccount rawMaterialStock()        { return byCode(RAW_MATERIAL_STOCK); }
    public LedgerAccount wip()                     { return byCode(WIP_STOCK); }
    public LedgerAccount finishedGoodsStock()      { return byCode(FINISHED_GOODS_STOCK); }
    public LedgerAccount grIrClearing()            { return byCode(GR_IR_CLEARING); }
    public LedgerAccount cogs()                    { return byCode(COGS); }
    public LedgerAccount inventoryAdjustment()     { return byCode(INVENTORY_ADJUSTMENT); }
    public LedgerAccount inventoryAdjustmentGain() { return byCode(INVENTORY_ADJUSTMENT_GAIN); }
    public LedgerAccount openingBalanceEquity()    { return byCode(OPENING_BALANCE_EQUITY); }

    // ─ Statutory deductions (Phase 4) ─
    public LedgerAccount tdsPayable()              { return byCode(TDS_PAYABLE); }

    // ─ Payroll (Phase 4) ─
    public LedgerAccount salariesAndWages()        { return byCode(SALARIES_WAGES); }
    public LedgerAccount salaryPayable()           { return byCode(SALARY_PAYABLE); }
    public LedgerAccount pfPayable()               { return byCode(PF_PAYABLE); }
    public LedgerAccount esiPayable()              { return byCode(ESI_PAYABLE); }
    public LedgerAccount professionalTaxPayable()  { return byCode(PT_PAYABLE); }

    // ─ Depreciation (Phase 4) ─
    public LedgerAccount depreciationExpense()       { return byCode(DEPRECIATION_EXPENSE); }
    public LedgerAccount accumulatedDepreciation()   { return byCode(ACCUMULATED_DEPRECIATION); }

    /**
     * Resolves an item's "home" stock ledger from its {@link ItemType}: finished/semi-finished
     * goods sit in Finished Goods Stock; everything else (raw material, consumable, sub-contracted)
     * in Raw Material Stock. WIP (2011) is never an item's home — it is only the work-order bridge.
     */
    public LedgerAccount stockLedgerFor(InventoryItem item) {
        ItemType type = item != null ? item.getItemType() : null;
        if (type == ItemType.FINISHED_GOOD || type == ItemType.SEMI_FINISHED) {
            return finishedGoodsStock();
        }
        return rawMaterialStock();
    }
}
