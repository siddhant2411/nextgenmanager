package com.nextgenmanager.nextgenmanager.accounting.close.service;

import com.nextgenmanager.nextgenmanager.accounting.close.dto.YearEndClosePreviewDto;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FyStatus;
import com.nextgenmanager.nextgenmanager.accounting.period.model.PeriodStatus;
import com.nextgenmanager.nextgenmanager.accounting.period.repository.AccountingPeriodRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.repository.FinancialYearRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.service.FinancialYearService;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.TrialBalanceDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.service.AccountingReportService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** FY 2025-26 (already over): sales 30,000, cost of sales 12,000 → profit 18,000. */
@ExtendWith(MockitoExtension.class)
class YearEndCloseServiceImplTest {

    @Mock private FinancialYearRepository fyRepo;
    @Mock private AccountingPeriodRepository periodRepo;
    @Mock private FinancialYearService fyService;
    @Mock private LedgerAccountRepository ledgerRepo;
    @Mock private VoucherLineRepository voucherLineRepo;
    @Mock private VoucherRepository voucherRepo;
    @Mock private AccountingReportService reportService;
    @Mock private PostingService postingService;

    @InjectMocks private YearEndCloseServiceImpl service;

    private static final LocalDate START = LocalDate.of(2025, 4, 1);
    private static final LocalDate END = LocalDate.of(2026, 3, 31);

    private FinancialYear fy;
    private List<AccountingPeriod> periods;
    private LedgerAccount bank, sales, cogs, retained;

    @BeforeEach
    void setUp() {
        fy = year(1L, "FY 2025-26", START, FyStatus.ACTIVE);
        periods = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            AccountingPeriod p = new AccountingPeriod();
            p.setId(100L + i);
            p.setFinancialYear(fy);
            p.setPeriodNumber(i + 1);
            p.setStartDate(START.plusMonths(i));
            p.setEndDate(START.plusMonths(i + 1).minusDays(1));
            p.setStatus(PeriodStatus.LOCKED);     // GST filed for every month
            periods.add(p);
        }
        bank = ledger(10L, "4011", "Bank", AccountNature.ASSET);
        sales = ledger(20L, "4010I", "Sales - Domestic", AccountNature.INCOME);
        cogs = ledger(30L, "5020E", "COGS", AccountNature.EXPENSE);
        retained = ledger(40L, "3020E", "Retained Earnings", AccountNature.EQUITY);

        lenient().when(fyRepo.findById(1L)).thenReturn(Optional.of(fy));
        lenient().when(fyRepo.findAll()).thenReturn(List.of(fy));
        lenient().when(periodRepo.findByFinancialYearIdOrderByPeriodNumber(1L)).thenReturn(periods);
        lenient().when(ledgerRepo.findAllWithGroupOrderByCode()).thenReturn(List.of(bank, sales, cogs, retained));
        lenient().when(ledgerRepo.findByCodeAndDeletedDateIsNull("3020E")).thenReturn(Optional.of(retained));
        lenient().when(voucherLineRepo.aggregateByAccount(END)).thenReturn(List.of(
                new Object[]{10L, new BigDecimal("30000"), new BigDecimal("12000")},
                new Object[]{20L, BigDecimal.ZERO, new BigDecimal("30000")},
                new Object[]{30L, new BigDecimal("12000"), BigDecimal.ZERO}));
        lenient().when(reportService.trialBalance(END)).thenReturn(tb(true));
        lenient().when(voucherRepo.countByDeletedDateIsNullAndStatusAndDateBetween(VoucherStatus.PENDING_APPROVAL, START, END)).thenReturn(0L);
    }

    @Test
    void preview_listsTheLedgersToClearAndPassesEveryCheck() {
        YearEndClosePreviewDto p = service.preview(1L);

        assertThat(p.isCanClose()).isTrue();
        assertThat(p.getNetProfit()).isEqualByComparingTo("18000");
        assertThat(p.getLines()).extracting(YearEndClosePreviewDto.Line::getCode).containsExactly("4010I", "5020E");
        YearEndClosePreviewDto.Line salesLine = p.getLines().get(0);
        assertThat(salesLine.getBalance()).isEqualByComparingTo("30000");
        assertThat(salesLine.getClosingDr()).isEqualByComparingTo("30000");
        assertThat(salesLine.getClosingCr()).isEqualByComparingTo("0");
    }

    @Test
    void close_postsClosingVoucherIntoTheLockedLastMonth_thenClosesEverything() {
        AtomicReference<PeriodStatus> lastMonthAtPost = new AtomicReference<>();
        ArgumentCaptor<VoucherDraft> draft = ArgumentCaptor.forClass(VoucherDraft.class);
        when(postingService.post(draft.capture(), eq("priya"))).thenAnswer(inv -> {
            lastMonthAtPost.set(periods.get(11).getStatus());
            VoucherDto v = new VoucherDto();
            v.setId(555L);
            return v;
        });

        service.close(1L, "priya");

        assertThat(lastMonthAtPost.get()).isEqualTo(PeriodStatus.OPEN);          // opened just for the post
        VoucherDraft d = draft.getValue();
        assertThat(d.getVoucherType()).isEqualTo(VoucherType.CLOSING);
        assertThat(d.getDate()).isEqualTo(END);
        assertThat(d.getSourceDocType()).isEqualTo(SourceDocTypes.FY_CLOSE);
        assertThat(d.getSourceDocId()).isNull();                                  // re-closable after a reopen
        assertThat(lineFor(d, 20L).getDrAmount()).isEqualByComparingTo("30000"); // sales cleared
        assertThat(lineFor(d, 30L).getCrAmount()).isEqualByComparingTo("12000"); // cost of sales cleared
        assertThat(lineFor(d, 40L).getCrAmount()).isEqualByComparingTo("18000"); // profit to Retained Earnings
        assertThat(d.getLines()).noneMatch(l -> l.getLedgerAccountId().equals(10L)); // balance-sheet ledgers untouched

        assertThat(periods).allMatch(p -> p.getStatus() == PeriodStatus.CLOSED);
        assertThat(fy.getStatus()).isEqualTo(FyStatus.CLOSED);
        assertThat(fy.getClosingVoucherId()).isEqualTo(555L);
        assertThat(fy.getClosedBy()).isEqualTo("priya");
    }

    @Test
    void close_aLoss_debitsRetainedEarnings() {
        when(voucherLineRepo.aggregateByAccount(END)).thenReturn(List.of(
                new Object[]{20L, BigDecimal.ZERO, new BigDecimal("5000")},
                new Object[]{30L, new BigDecimal("7000"), BigDecimal.ZERO}));
        ArgumentCaptor<VoucherDraft> draft = ArgumentCaptor.forClass(VoucherDraft.class);
        VoucherDto v = new VoucherDto();
        v.setId(9L);
        when(postingService.post(draft.capture(), any())).thenReturn(v);

        service.close(1L, "priya");

        assertThat(lineFor(draft.getValue(), 40L).getDrAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void close_refusedWhenTheBooksDoNotBalance() {
        when(reportService.trialBalance(END)).thenReturn(tb(false));

        assertThatThrownBy(() -> service.close(1L, "priya"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("debits and credits differ");
        verify(postingService, never()).post(any(), any());
        assertThat(fy.getStatus()).isEqualTo(FyStatus.ACTIVE);
    }

    @Test
    void close_refusedWhileAnEarlierYearIsOpen() {
        FinancialYear earlier = year(2L, "FY 2024-25", START.minusYears(1), FyStatus.ACTIVE);
        when(fyRepo.findAll()).thenReturn(List.of(earlier, fy));

        assertThatThrownBy(() -> service.close(1L, "priya"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FY 2024-25");
    }

    @Test
    void preview_aYearStillRunning_cannotClose() {
        LocalDate thisStart = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        FinancialYear running = year(3L, "FY running", thisStart, FyStatus.ACTIVE);
        when(fyRepo.findById(3L)).thenReturn(Optional.of(running));
        when(reportService.trialBalance(running.getEndDate())).thenReturn(tb(true));

        YearEndClosePreviewDto p = service.preview(3L);

        assertThat(p.isCanClose()).isFalse();
        assertThat(p.getChecks()).filteredOn(c -> c.getCode().equals("YEAR_ENDED")).allMatch(c -> !c.isPassed());
    }

    @Test
    void reopen_reversesTheClosingVoucher_andLocksTheMonths() {
        fy.setStatus(FyStatus.CLOSED);
        fy.setClosingVoucherId(555L);
        periods.forEach(p -> p.setStatus(PeriodStatus.CLOSED));
        AtomicReference<PeriodStatus> lastMonthAtReverse = new AtomicReference<>();
        when(postingService.reverse(eq(555L), contains("wrong depreciation"), eq("priya"))).thenAnswer(inv -> {
            lastMonthAtReverse.set(periods.get(11).getStatus());
            return new VoucherDto();
        });

        service.reopen(1L, "wrong depreciation", "priya");

        assertThat(lastMonthAtReverse.get()).isEqualTo(PeriodStatus.OPEN);
        assertThat(periods).allMatch(p -> p.getStatus() == PeriodStatus.LOCKED);
        assertThat(fy.getStatus()).isEqualTo(FyStatus.ACTIVE);
        assertThat(fy.getClosingVoucherId()).isNull();
        assertThat(fy.getReopenReason()).isEqualTo("wrong depreciation");
    }

    @Test
    void reopen_refusedWhileALaterYearIsClosed_orWithoutAReason() {
        fy.setStatus(FyStatus.CLOSED);
        FinancialYear later = year(4L, "FY 2026-27", START.plusYears(1), FyStatus.CLOSED);
        when(fyRepo.findAll()).thenReturn(List.of(fy, later));

        assertThatThrownBy(() -> service.reopen(1L, "fix", "priya"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FY 2026-27");
        assertThatThrownBy(() -> service.reopen(1L, "  ", "priya"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(postingService, never()).reverse(any(), any(), any());
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private static FinancialYear year(Long id, String label, LocalDate start, FyStatus status) {
        FinancialYear y = new FinancialYear();
        y.setId(id);
        y.setLabel(label);
        y.setStartDate(start);
        y.setEndDate(start.plusYears(1).minusDays(1));
        y.setStatus(status);
        return y;
    }

    private static LedgerAccount ledger(Long id, String code, String name, AccountNature nature) {
        LedgerAccount la = new LedgerAccount();
        la.setId(id);
        la.setCode(code);
        la.setName(name);
        la.setNature(nature);
        return la;
    }

    private static TrialBalanceDto tb(boolean balanced) {
        TrialBalanceDto t = new TrialBalanceDto();
        t.setBalanced(balanced);
        return t;
    }

    private static VoucherLineDraft lineFor(VoucherDraft d, Long accountId) {
        return d.getLines().stream().filter(l -> l.getLedgerAccountId().equals(accountId)).findFirst().orElseThrow();
    }
}
