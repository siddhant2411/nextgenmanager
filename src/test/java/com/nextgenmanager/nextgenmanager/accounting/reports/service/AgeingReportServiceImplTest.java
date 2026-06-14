package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.AgeingReportDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.AgeingRowDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgeingReportServiceImplTest {

    @Mock private VoucherLineRepository voucherLineRepo;
    @Mock private LedgerAccountRepository ledgerRepo;

    @InjectMocks private AgeingReportServiceImpl service;

    private static final LocalDate AS_OF = LocalDate.of(2025, 6, 14);

    private LedgerAccount ledger(long id, String code, String name) {
        LedgerAccount la = mock(LedgerAccount.class);
        lenient().when(la.getId()).thenReturn(id);
        lenient().when(la.getCode()).thenReturn(code);
        lenient().when(la.getName()).thenReturn(name);
        return la;
    }

    private Object[] line(long ledgerId, LocalDate date, String dr, String cr) {
        return new Object[]{ ledgerId, date, new BigDecimal(dr), new BigDecimal(cr) };
    }

    @Test
    void debtors_fifoAllocatesReceiptToOldestInvoice_andBucketsByAge() {
        LedgerAccount acme = ledger(3001L, "C-3001", "Acme Pvt Ltd");
        when(ledgerRepo.findBySubLedgerTypeAndDeletedDateIsNull(SubLedgerType.CUSTOMER))
                .thenReturn(List.of(acme));
        when(voucherLineRepo.partyLedgerLines(SubLedgerType.CUSTOMER, AS_OF)).thenReturn(List.of(
                line(3001L, LocalDate.of(2025, 1, 10), "1000.00", "0.00"),  // old invoice -> 90+
                line(3001L, LocalDate.of(2025, 5, 20), "500.00",  "0.00"),  // recent invoice -> current
                line(3001L, LocalDate.of(2025, 5, 25), "0.00",    "300.00") // receipt: FIFO to oldest
        ));

        AgeingReportDto report = service.debtorsAgeing(AS_OF);

        assertThat(report.getType()).isEqualTo("DEBTORS");
        assertThat(report.getRows()).hasSize(1);
        AgeingRowDto row = report.getRows().get(0);
        assertThat(row.getPartyName()).isEqualTo("Acme Pvt Ltd");
        assertThat(row.getCurrent()).isEqualByComparingTo("500.00");      // recent invoice untouched
        assertThat(row.getDays31_60()).isEqualByComparingTo("0");
        assertThat(row.getDays61_90()).isEqualByComparingTo("0");
        assertThat(row.getDays90Plus()).isEqualByComparingTo("700.00");   // 1000 - 300 receipt
        assertThat(row.getTotal()).isEqualByComparingTo("1200.00");
        // Buckets tie to the total, and total ties to the net ledger balance (1500 dr - 300 cr).
        assertThat(report.getTotals().getTotal()).isEqualByComparingTo("1200.00");
    }

    @Test
    void debtors_fullySettledParty_isHidden() {
        LedgerAccount paid = ledger(3002L, "C-3002", "Settled Co");
        when(ledgerRepo.findBySubLedgerTypeAndDeletedDateIsNull(SubLedgerType.CUSTOMER))
                .thenReturn(List.of(paid));
        when(voucherLineRepo.partyLedgerLines(SubLedgerType.CUSTOMER, AS_OF)).thenReturn(List.of(
                line(3002L, LocalDate.of(2025, 4, 1), "800.00", "0.00"),
                line(3002L, LocalDate.of(2025, 4, 30), "0.00", "800.00")
        ));

        AgeingReportDto report = service.debtorsAgeing(AS_OF);

        assertThat(report.getRows()).isEmpty();
        assertThat(report.getTotals().getTotal()).isEqualByComparingTo("0");
    }

    @Test
    void creditors_chargeIsCreditPaymentIsDebit() {
        LedgerAccount supplier = ledger(8001L, "V-8001", "Supplier Ltd");
        when(ledgerRepo.findBySubLedgerTypeAndDeletedDateIsNull(SubLedgerType.VENDOR))
                .thenReturn(List.of(supplier));
        when(voucherLineRepo.partyLedgerLines(SubLedgerType.VENDOR, AS_OF)).thenReturn(List.of(
                line(8001L, LocalDate.of(2025, 3, 1), "0.00", "2000.00"),  // vendor bill (charge) -> 90+
                line(8001L, LocalDate.of(2025, 6, 1), "0.00", "1000.00"),  // vendor bill -> current
                line(8001L, LocalDate.of(2025, 6, 5), "1500.00", "0.00")   // payment: FIFO to oldest
        ));

        AgeingReportDto report = service.creditorsAgeing(AS_OF);

        assertThat(report.getType()).isEqualTo("CREDITORS");
        AgeingRowDto row = report.getRows().get(0);
        assertThat(row.getDays90Plus()).isEqualByComparingTo("500.00");   // 2000 - 1500 payment
        assertThat(row.getCurrent()).isEqualByComparingTo("1000.00");
        assertThat(row.getTotal()).isEqualByComparingTo("1500.00");
    }
}
