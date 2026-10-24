package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.production.enums.CostType;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OverheadPolicyTest {

    private static final BigDecimal TWENTY = new BigDecimal("20");

    @Test
    void anyBlanketRate_zeroIncluded_suppressesWorkCenterOverhead() {
        assertThat(OverheadPolicy.workCenterPercentage(new BigDecimal("45"), CostType.CALCULATED, null, wc()))
                .isEqualByComparingTo("0");
        assertThat(OverheadPolicy.workCenterPercentage(BigDecimal.ZERO, CostType.CALCULATED, null, wc()))
                .isEqualByComparingTo("0");
        assertThat(OverheadPolicy.blanketPercentage(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(OverheadPolicy.usesBlanket(BigDecimal.ZERO)).isTrue();
    }

    @Test
    void blankBlanketRate_calculatedOperationTakesWorkCenterRate() {
        assertThat(OverheadPolicy.usesBlanket(null)).isFalse();
        assertThat(OverheadPolicy.blanketPercentage(null)).isEqualByComparingTo("0");
        assertThat(OverheadPolicy.workCenterPercentage(null, CostType.CALCULATED, null, wc()))
                .isEqualByComparingTo(TWENTY);
        assertThat(OverheadPolicy.workCenterPercentage(null, null, null, wc()))   // default type
                .isEqualByComparingTo(TWENTY);
    }

    @Test
    void blankBlanketRate_pieceRateOnlyWhenOptedIn() {
        assertThat(OverheadPolicy.workCenterPercentage(null, CostType.RATE_TIMES_QTY, false, wc()))
                .isEqualByComparingTo("0");
        assertThat(OverheadPolicy.workCenterPercentage(null, CostType.RATE_TIMES_QTY, null, wc()))
                .isEqualByComparingTo("0");
        assertThat(OverheadPolicy.workCenterPercentage(null, CostType.RATE_TIMES_QTY, true, wc()))
                .isEqualByComparingTo(TWENTY);
    }

    @Test
    void blankBlanketRate_flatOperationsNeverTakeWorkCenterRate() {
        assertThat(OverheadPolicy.workCenterPercentage(null, CostType.FIXED_RATE, true, wc()))
                .isEqualByComparingTo("0");
        assertThat(OverheadPolicy.workCenterPercentage(null, CostType.SUB_CONTRACTED, true, wc()))
                .isEqualByComparingTo("0");
        assertThat(OverheadPolicy.workCenterPercentage(null, CostType.CALCULATED, null, null))
                .isEqualByComparingTo("0");
    }

    private static WorkCenter wc() {
        WorkCenter wc = new WorkCenter();
        wc.setOverheadPercentage(TWENTY);
        return wc;
    }
}
