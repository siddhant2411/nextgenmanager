package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.StockGlReconciliationDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.StockGlReconciliationRowDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockGlReconciliationServiceImplTest {

    @Mock private VoucherLineRepository voucherLineRepo;
    @Mock private InventoryLedgerRepository inventoryLedgerRepo;
    @Mock private LedgerAccountRepository ledgerRepo;

    @InjectMocks private StockGlReconciliationServiceImpl service;

    private static final LocalDate AS_OF = LocalDate.of(2025, 6, 30);

    /** GRN 1000 (RM) → consume 400 → produce 400 (FG) → dispatch 400. Net: RM 600, WIP 0, FG 0. */
    private List<Object[]> movements() {
        return List.of(
                new Object[]{"GRN", "GRN", 10.0, 1000.0, ItemType.RAW_MATERIAL},
                new Object[]{"CONSUME", "WORK_ORDER", -4.0, 400.0, ItemType.RAW_MATERIAL},
                new Object[]{"PRODUCE", "WORK_ORDER", 4.0, 400.0, ItemType.FINISHED_GOOD},
                new Object[]{"SALES_DISPATCH", "DELIVERY_NOTE", -4.0, 400.0, ItemType.FINISHED_GOOD}
        );
    }

    private StockGlReconciliationRowDto rowFor(StockGlReconciliationDto d, String code) {
        return d.getRows().stream().filter(r -> r.getCode().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void stockMovementsPosted_glTiesToInventoryLedger() {
        when(inventoryLedgerRepo.movementsForReconciliation(AS_OF)).thenReturn(movements());
        when(voucherLineRepo.balanceByCodeAsOf(eq(AS_OF), any())).thenReturn(List.<Object[]>of(
                new Object[]{"2010", new BigDecimal("600"), BigDecimal.ZERO}, // RM GL = 600
                new Object[]{"6030", BigDecimal.ZERO, new BigDecimal("1000")} // GR/IR net credit 1000 (not invoiced)
        ));
        when(ledgerRepo.findByCodeAndDeletedDateIsNull(any())).thenReturn(Optional.empty());

        StockGlReconciliationDto d = service.reconcile(null, AS_OF);

        assertThat(d.getRows()).hasSize(3);
        StockGlReconciliationRowDto rm = rowFor(d, "2010");
        assertThat(rm.getStockValue()).isEqualByComparingTo("600.00");
        assertThat(rm.getGlBalance()).isEqualByComparingTo("600.00");
        assertThat(rm.getVariance()).isEqualByComparingTo("0.00");
        assertThat(rm.isTiesOut()).isTrue();
        assertThat(rowFor(d, "2011").isTiesOut()).isTrue();  // WIP nets to 0 on both sides
        assertThat(rowFor(d, "2012").isTiesOut()).isTrue();  // FG nets to 0 on both sides
        assertThat(d.isTiesOut()).isTrue();
        assertThat(d.getGrIrBalance()).isEqualByComparingTo("1000.00"); // goods received not invoiced
    }

    @Test
    void missingGlPosting_flagsVariance() {
        when(inventoryLedgerRepo.movementsForReconciliation(AS_OF)).thenReturn(movements());
        when(voucherLineRepo.balanceByCodeAsOf(eq(AS_OF), any())).thenReturn(List.<Object[]>of(
                new Object[]{"2010", new BigDecimal("200"), BigDecimal.ZERO} // a consume failed to post → GL short
        ));
        when(ledgerRepo.findByCodeAndDeletedDateIsNull(any())).thenReturn(Optional.empty());

        StockGlReconciliationDto d = service.reconcile(null, AS_OF);

        StockGlReconciliationRowDto rm = rowFor(d, "2010");
        assertThat(rm.getStockValue()).isEqualByComparingTo("600.00");
        assertThat(rm.getGlBalance()).isEqualByComparingTo("200.00");
        assertThat(rm.getVariance()).isEqualByComparingTo("-400.00");
        assertThat(rm.isTiesOut()).isFalse();
        assertThat(d.isTiesOut()).isFalse();
    }
}
