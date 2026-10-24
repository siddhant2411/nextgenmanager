package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.production.enums.CostType;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one rule for which overhead a manufactured item carries. Every costing path — BOM cost
 * breakdown, work-order cost report, make/buy analysis and the work-order completion that values
 * finished-good stock — asks here, so they can never disagree.
 *
 * <ul>
 *   <li><b>BOM overhead % set</b> (any value, 0 included): that blanket rate is the only overhead.
 *       It is applied once at the rollup; no operation carries work-center overhead.</li>
 *   <li><b>BOM overhead % blank</b> (null): each operation carries its own work center's
 *       overhead % on its conversion cost —
 *       <ul>
 *         <li>CALCULATED: always;</li>
 *         <li>RATE_TIMES_QTY: only when the operation's {@code applyOverhead} flag is on
 *             (a piece rate is treated as all-in otherwise);</li>
 *         <li>FIXED_RATE / SUB_CONTRACTED: never.</li>
 *       </ul></li>
 * </ul>
 *
 * Work-center overhead is a real cost of the part and stays in its stored standard cost; only the
 * blanket overhead is stripped before storing, because each level re-applies its own blanket rate.
 */
public final class OverheadPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private OverheadPolicy() {
    }

    /** True when the BOM's blanket rate governs overhead. Only null falls back to work centers. */
    public static boolean usesBlanket(BigDecimal bomOverheadPercentage) {
        return bomOverheadPercentage != null;
    }

    /** The blanket % to apply at the rollup, or zero in work-center mode. */
    public static BigDecimal blanketPercentage(BigDecimal bomOverheadPercentage) {
        return usesBlanket(bomOverheadPercentage) ? bomOverheadPercentage : BigDecimal.ZERO;
    }

    /** The work center's overhead % to load on one operation's conversion cost, or zero. */
    public static BigDecimal workCenterPercentage(BigDecimal bomOverheadPercentage, CostType costType,
                                                  Boolean applyOverhead, WorkCenter workCenter) {
        if (usesBlanket(bomOverheadPercentage) || workCenter == null
                || workCenter.getOverheadPercentage() == null) {
            return BigDecimal.ZERO;
        }
        CostType type = costType != null ? costType : CostType.CALCULATED;
        boolean loads = type == CostType.CALCULATED
                || (type == CostType.RATE_TIMES_QTY && Boolean.TRUE.equals(applyOverhead));
        return loads ? workCenter.getOverheadPercentage() : BigDecimal.ZERO;
    }

    /** amount × pct / 100, unrounded beyond 4 places — callers round to their own scale. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal pct) {
        if (amount == null || pct == null || pct.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(pct).divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }
}
