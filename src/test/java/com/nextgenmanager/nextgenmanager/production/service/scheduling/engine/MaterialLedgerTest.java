package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialLedgerTest {

    private static final int STEEL = 1;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 8, 0);
    private static final LocalDateTime PO_DAY = LocalDateTime.of(2026, 10, 8, 0, 0);
    private static final LocalDateTime LATER_PO_DAY = LocalDateTime.of(2026, 10, 12, 0, 0);

    @Test
    void stockThatCoversTheNeedIsThereNow() {
        MaterialLedger ledger = new MaterialLedger().supply(STEEL, NOW, 100, "stock");

        MaterialLedger.Allocation allocation = ledger.allocate(STEEL, 40);

        assertThat(allocation.readyAt()).isEqualTo(NOW);
        assertThat(allocation.source()).isEqualTo("stock");
    }

    @Test
    void aNeedBiggerThanStockWaitsForTheOrderThatCoversTheRest() {
        MaterialLedger ledger = new MaterialLedger()
                .supply(STEEL, NOW, 30, "stock")
                .supply(STEEL, PO_DAY, 100, "PO/0031");

        MaterialLedger.Allocation allocation = ledger.allocate(STEEL, 50);

        assertThat(allocation.readyAt()).isEqualTo(PO_DAY);
        assertThat(allocation.source()).isEqualTo("PO/0031");
    }

    @Test
    void whoeverClaimsFirstTakesTheStockAndTheNextWaits() {
        MaterialLedger ledger = new MaterialLedger()
                .supply(STEEL, NOW, 60, "stock")
                .supply(STEEL, PO_DAY, 100, "PO/0031");

        assertThat(ledger.allocate(STEEL, 50).readyAt()).isEqualTo(NOW);
        assertThat(ledger.allocate(STEEL, 50).readyAt()).isEqualTo(PO_DAY);
    }

    @Test
    void supplyIsUsedEarliestFirstWhateverOrderItWasAddedIn() {
        MaterialLedger ledger = new MaterialLedger()
                .supply(STEEL, LATER_PO_DAY, 100, "PO/0040")
                .supply(STEEL, PO_DAY, 100, "PO/0031");

        assertThat(ledger.allocate(STEEL, 80).source()).isEqualTo("PO/0031");
        assertThat(ledger.allocate(STEEL, 80).source()).isEqualTo("PO/0040");
    }

    @Test
    void beyondKnownSupplyTheItemComesInItsLeadTime() {
        LocalDateTime inLeadTime = NOW.plusDays(7);
        MaterialLedger ledger = new MaterialLedger()
                .supply(STEEL, NOW, 10, "stock")
                .fallback(STEEL, inLeadTime, "lead time 7 days");

        MaterialLedger.Allocation allocation = ledger.allocate(STEEL, 25);

        assertThat(allocation.readyAt()).isEqualTo(inLeadTime);
        assertThat(allocation.source()).isEqualTo("lead time 7 days");
    }

    @Test
    void withNoSupplyAndNoLeadTimeANeedCannotBeDated() {
        MaterialLedger ledger = new MaterialLedger().supply(STEEL, NOW, 10, "stock");

        assertThat(ledger.allocate(STEEL, 25).dated()).isFalse();
        assertThat(ledger.allocate(2, 1).dated()).isFalse();
    }

    @Test
    void nothingNeededIsNotAClaim() {
        MaterialLedger ledger = new MaterialLedger().supply(STEEL, NOW, 10, "stock");

        ledger.allocate(STEEL, 0);

        assertThat(ledger.allocate(STEEL, 10).readyAt()).isEqualTo(NOW);
    }

    @Test
    void aCopyCanBeClaimedFromWithoutTouchingTheOriginal() {
        MaterialLedger ledger = new MaterialLedger()
                .supply(STEEL, NOW, 50, "stock")
                .supply(STEEL, PO_DAY, 100, "PO/0031");

        MaterialLedger copy = ledger.copy();
        copy.allocate(STEEL, 50);

        assertThat(copy.allocate(STEEL, 10).readyAt()).isEqualTo(PO_DAY);
        assertThat(ledger.allocate(STEEL, 50).readyAt()).isEqualTo(NOW);
    }
}
