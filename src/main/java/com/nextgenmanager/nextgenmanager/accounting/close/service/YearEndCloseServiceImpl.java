package com.nextgenmanager.nextgenmanager.accounting.close.service;

import com.nextgenmanager.nextgenmanager.accounting.close.dto.YearEndClosePreviewDto;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.dto.FinancialYearDto;
import com.nextgenmanager.nextgenmanager.accounting.period.model.AccountingPeriod;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FinancialYear;
import com.nextgenmanager.nextgenmanager.accounting.period.model.FyStatus;
import com.nextgenmanager.nextgenmanager.accounting.period.model.PeriodStatus;
import com.nextgenmanager.nextgenmanager.accounting.period.repository.AccountingPeriodRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.repository.FinancialYearRepository;
import com.nextgenmanager.nextgenmanager.accounting.period.service.FinancialYearService;
import com.nextgenmanager.nextgenmanager.accounting.posting.LedgerResolver;
import com.nextgenmanager.nextgenmanager.accounting.reports.service.AccountingReportService;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.dto.VoucherLineDraft;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.service.PostingService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Year-end close. Balance-sheet ledgers need no carry-forward entry: every statement reads them
 * cumulatively, so their closing balance simply is next year's opening. Only income and expense
 * ledgers are reset, by one CLOSING voucher dated the last day of the year that moves their
 * balances to Retained Earnings. The P&L leaves CLOSING vouchers out, so a closed year's P&L still
 * reads the same.
 *
 * <p>The last month may already be LOCKED (e.g. after GST filing) — closing does not require the
 * months to be locked or unlocked. It opens that month just long enough to post, inside the same
 * transaction, then marks every month CLOSED.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class YearEndCloseServiceImpl implements YearEndCloseService {

    private final FinancialYearRepository fyRepo;
    private final AccountingPeriodRepository periodRepo;
    private final FinancialYearService fyService;
    private final LedgerAccountRepository ledgerRepo;
    private final VoucherLineRepository voucherLineRepo;
    private final VoucherRepository voucherRepo;
    private final AccountingReportService reportService;
    private final PostingService postingService;

    // ─── Preview ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public YearEndClosePreviewDto preview(Long financialYearId) {
        FinancialYear fy = findYear(financialYearId);
        YearEndClosePreviewDto dto = new YearEndClosePreviewDto();
        dto.setFinancialYearId(fy.getId());
        dto.setLabel(fy.getLabel());
        dto.setStartDate(fy.getStartDate());
        dto.setEndDate(fy.getEndDate());
        dto.setStatus(fy.getStatus());

        // The closing lines: every income/expense ledger with a balance at year-end, cleared to zero.
        Map<Long, BigDecimal> net = new HashMap<>();
        for (Object[] r : voucherLineRepo.aggregateByAccount(fy.getEndDate())) {
            net.put((Long) r[0], nz((BigDecimal) r[1]).subtract(nz((BigDecimal) r[2])));
        }
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        for (LedgerAccount la : ledgerRepo.findAllWithGroupOrderByCode()) {
            boolean isIncome = la.getNature() == AccountNature.INCOME;
            if (!isIncome && la.getNature() != AccountNature.EXPENSE) continue;
            BigDecimal drMinusCr = money(net.getOrDefault(la.getId(), BigDecimal.ZERO).add(typedOpening(la)));
            if (drMinusCr.signum() == 0) continue;
            BigDecimal balance = isIncome ? drMinusCr.negate() : drMinusCr;
            if (isIncome) income = income.add(balance); else expenses = expenses.add(balance);
            dto.getLines().add(new YearEndClosePreviewDto.Line(la.getId(), la.getCode(), la.getName(), la.getNature().name(),
                    balance,
                    drMinusCr.signum() < 0 ? drMinusCr.negate() : BigDecimal.ZERO.setScale(2),   // a credit balance is cleared by a debit
                    drMinusCr.signum() > 0 ? drMinusCr : BigDecimal.ZERO.setScale(2)));
        }
        dto.setTotalIncome(income);
        dto.setTotalExpenses(expenses);
        dto.setNetProfit(income.subtract(expenses));

        Optional<LedgerAccount> retained = ledgerRepo.findByCodeAndDeletedDateIsNull(LedgerResolver.RETAINED_EARNINGS);
        retained.ifPresent(r -> { dto.setRetainedEarningsCode(r.getCode()); dto.setRetainedEarningsName(r.getName()); });

        // Checks
        List<YearEndClosePreviewDto.Check> checks = dto.getChecks();
        boolean open = fy.getStatus() == FyStatus.ACTIVE;
        checks.add(new YearEndClosePreviewDto.Check("YEAR_OPEN", "The year is open", open,
                open ? null : "Already closed" + (fy.getClosedBy() != null ? " by " + fy.getClosedBy() : "") + "."));

        boolean ended = LocalDate.now().isAfter(fy.getEndDate());
        checks.add(new YearEndClosePreviewDto.Check("YEAR_ENDED", "The year has ended", ended,
                ended ? null : "The year runs to " + fy.getEndDate() + ". Closing it now would shut its remaining months."));

        List<String> openEarlier = fyRepo.findAll().stream()
                .filter(other -> other.getEndDate().isBefore(fy.getStartDate()) && other.getStatus() != FyStatus.CLOSED)
                .sorted(Comparator.comparing(FinancialYear::getStartDate))
                .map(FinancialYear::getLabel).toList();
        checks.add(new YearEndClosePreviewDto.Check("EARLIER_YEARS_CLOSED", "Earlier years are closed", openEarlier.isEmpty(),
                openEarlier.isEmpty() ? null : "Close " + String.join(", ", openEarlier) + " first — years close in order."));

        boolean balanced = reportService.trialBalance(fy.getEndDate()).isBalanced();
        checks.add(new YearEndClosePreviewDto.Check("TRIAL_BALANCE", "The trial balance balances at year-end", balanced,
                balanced ? null : "Total debits and credits differ as at " + fy.getEndDate() + ". Find and correct the difference first."));

        long pending = voucherRepo.countByDeletedDateIsNullAndStatusAndDateBetween(
                VoucherStatus.PENDING_APPROVAL, fy.getStartDate(), fy.getEndDate());
        checks.add(new YearEndClosePreviewDto.Check("NO_PENDING_APPROVALS", "No vouchers are waiting for approval", pending == 0,
                pending == 0 ? null : pending + " voucher(s) dated in the year still wait for approval. Approve or reject them first."));

        boolean needsRetained = !dto.getLines().isEmpty();
        boolean hasRetained = retained.isPresent();
        checks.add(new YearEndClosePreviewDto.Check("RETAINED_EARNINGS", "A Retained Earnings ledger exists", !needsRetained || hasRetained,
                !needsRetained || hasRetained ? null : "Ledger " + LedgerResolver.RETAINED_EARNINGS + " (Retained Earnings) is missing from the chart of accounts."));

        dto.setCanClose(checks.stream().allMatch(YearEndClosePreviewDto.Check::isPassed));
        return dto;
    }

    // ─── Close ───────────────────────────────────────────────────────────────

    @Override
    public FinancialYearDto close(Long financialYearId, String username) {
        YearEndClosePreviewDto preview = preview(financialYearId);
        if (!preview.isCanClose()) {
            String why = preview.getChecks().stream().filter(c -> !c.isPassed())
                    .map(c -> c.getDetail() != null ? c.getDetail() : c.getLabel())
                    .collect(Collectors.joining(" "));
            throw new IllegalStateException("Cannot close " + preview.getLabel() + ": " + why);
        }
        FinancialYear fy = findYear(financialYearId);
        List<AccountingPeriod> periods = periodRepo.findByFinancialYearIdOrderByPeriodNumber(fy.getId());
        AccountingPeriod last = lastPeriod(fy, periods);

        Long closingVoucherId = null;
        if (!preview.getLines().isEmpty()) {
            last.setStatus(PeriodStatus.OPEN);       // posting needs an OPEN month; it is CLOSED again below
            periodRepo.save(last);
            closingVoucherId = postingService.post(closingDraft(fy, preview), username).getId();
        }

        for (AccountingPeriod p : periods) p.setStatus(PeriodStatus.CLOSED);
        periodRepo.saveAll(periods);

        fy.setStatus(FyStatus.CLOSED);
        fy.setClosingVoucherId(closingVoucherId);
        fy.setClosedAt(new Date());
        fy.setClosedBy(username);
        fyRepo.save(fy);
        return fyService.getById(fy.getId());
    }

    private VoucherDraft closingDraft(FinancialYear fy, YearEndClosePreviewDto preview) {
        List<VoucherLineDraft> lines = new ArrayList<>();
        BigDecimal dr = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        for (YearEndClosePreviewDto.Line l : preview.getLines()) {
            lines.add(line(l.getAccountId(), l.getClosingDr(), l.getClosingCr(), "Year-end close"));
            dr = dr.add(l.getClosingDr());
            cr = cr.add(l.getClosingCr());
        }
        // Clearing income debits more than clearing expenses credits when there is a profit — the gap is credited to Retained Earnings.
        BigDecimal gap = dr.subtract(cr);
        if (gap.signum() != 0) {
            LedgerAccount retained = ledgerRepo.findByCodeAndDeletedDateIsNull(LedgerResolver.RETAINED_EARNINGS)
                    .orElseThrow(() -> new IllegalStateException("Retained Earnings ledger " + LedgerResolver.RETAINED_EARNINGS + " not found"));
            lines.add(gap.signum() > 0
                    ? line(retained.getId(), BigDecimal.ZERO, gap, "Profit for " + fy.getLabel())
                    : line(retained.getId(), gap.negate(), BigDecimal.ZERO, "Loss for " + fy.getLabel()));
        }

        VoucherDraft draft = new VoucherDraft();
        draft.setVoucherType(VoucherType.CLOSING);
        draft.setDate(fy.getEndDate());
        draft.setNarration("Year-end close " + fy.getLabel() + ": income and expenses transferred to Retained Earnings");
        draft.setSourceDocType(SourceDocTypes.FY_CLOSE);   // no sourceDocId — see SourceDocTypes.FY_CLOSE
        draft.setLines(lines);
        return draft;
    }

    private static VoucherLineDraft line(Long accountId, BigDecimal dr, BigDecimal cr, String narration) {
        VoucherLineDraft d = new VoucherLineDraft();
        d.setLedgerAccountId(accountId);
        d.setDrAmount(dr);
        d.setCrAmount(cr);
        d.setNarration(narration);
        return d;
    }

    // ─── Reopen ──────────────────────────────────────────────────────────────

    @Override
    public FinancialYearDto reopen(Long financialYearId, String reason, String username) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for reopening the year.");
        FinancialYear fy = findYear(financialYearId);
        if (fy.getStatus() != FyStatus.CLOSED) {
            throw new IllegalStateException(fy.getLabel() + " is not closed.");
        }
        List<String> closedLater = fyRepo.findAll().stream()
                .filter(other -> other.getStartDate().isAfter(fy.getEndDate()) && other.getStatus() == FyStatus.CLOSED)
                .sorted(Comparator.comparing(FinancialYear::getStartDate).reversed())
                .map(FinancialYear::getLabel).toList();
        if (!closedLater.isEmpty()) {
            throw new IllegalStateException("Reopen " + String.join(", ", closedLater) + " first — years reopen latest first.");
        }

        List<AccountingPeriod> periods = periodRepo.findByFinancialYearIdOrderByPeriodNumber(fy.getId());
        for (AccountingPeriod p : periods) p.setStatus(PeriodStatus.LOCKED);
        if (fy.getClosingVoucherId() != null) {
            AccountingPeriod last = lastPeriod(fy, periods);
            last.setStatus(PeriodStatus.OPEN);       // the reversal posts on the closing voucher's date
            periodRepo.saveAll(periods);
            VoucherDto reversal = postingService.reverse(fy.getClosingVoucherId(), "Year reopened: " + reason.trim(), username);
            if (reversal == null) throw new IllegalStateException("The closing voucher could not be reversed.");
            last.setStatus(PeriodStatus.LOCKED);
        }
        periodRepo.saveAll(periods);

        fy.setStatus(FyStatus.ACTIVE);
        fy.setClosingVoucherId(null);
        fy.setReopenedAt(new Date());
        fy.setReopenedBy(username);
        fy.setReopenReason(reason.trim());
        fyRepo.save(fy);
        return fyService.getById(fy.getId());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private FinancialYear findYear(Long id) {
        return fyRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Financial year not found: " + id));
    }

    private static AccountingPeriod lastPeriod(FinancialYear fy, List<AccountingPeriod> periods) {
        return periods.stream()
                .filter(p -> !p.getStartDate().isAfter(fy.getEndDate()) && !p.getEndDate().isBefore(fy.getEndDate()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No accounting period covers " + fy.getEndDate() + " in " + fy.getLabel()));
    }

    /** Opening balance typed onto the ledger itself (not via an OPENING voucher), as Dr − Cr — the trial balance counts it too. */
    private static BigDecimal typedOpening(LedgerAccount la) {
        BigDecimal ob = la.getOpeningBalance();
        if (ob == null || ob.signum() == 0) return BigDecimal.ZERO;
        return "DR".equals(la.getOpeningBalanceDrCr()) ? ob : ob.negate();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }
}
