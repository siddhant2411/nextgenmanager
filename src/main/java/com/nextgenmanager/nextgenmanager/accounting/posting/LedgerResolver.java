package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
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
}
