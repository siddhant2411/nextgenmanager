package com.nextgenmanager.nextgenmanager.accounting.posting;

import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.TaxLineType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Small shared helpers for building auto-posted voucher drafts. */
public final class PostingSupport {

    private PostingSupport() {}

    /**
     * Appends a balancing round-off line if Σdr ≠ Σcr (penny differences from source-doc
     * rounding). Resolves the round-off ledger only when actually needed.
     */
    public static void balanceWithRoundOff(List<VoucherLineDraft> lines, LedgerResolver ledgers) {
        BigDecimal dr = lines.stream().map(l -> nz(l.getDrAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(l -> nz(l.getCrAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = dr.subtract(cr).setScale(2, RoundingMode.HALF_UP);
        if (diff.signum() > 0) {
            lines.add(cr(ledgers.roundOffIncome().getId(), diff, "Round-off", null));
        } else if (diff.signum() < 0) {
            lines.add(dr(ledgers.roundOffExpense().getId(), diff.negate(), "Round-off", null));
        }
    }

    public static VoucherLineDraft line(Long ledgerId, BigDecimal dr, BigDecimal cr,
                                        String narration, TaxLineType taxType) {
        VoucherLineDraft l = new VoucherLineDraft();
        l.setLedgerAccountId(ledgerId);
        l.setDrAmount(dr);
        l.setCrAmount(cr);
        l.setNarration(narration);
        l.setTaxType(taxType);
        return l;
    }

    public static VoucherLineDraft dr(Long ledgerId, BigDecimal amount, String narration, TaxLineType taxType) {
        return line(ledgerId, amount, BigDecimal.ZERO, narration, taxType);
    }

    public static VoucherLineDraft cr(Long ledgerId, BigDecimal amount, String narration, TaxLineType taxType) {
        return line(ledgerId, BigDecimal.ZERO, amount, narration, taxType);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Converts a primitive double money amount to a scale-2 BigDecimal. */
    public static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
