package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountGroup;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.AccountGroupRepository;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.BalanceSheetDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.ProfitLossDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.StatementLineDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * A small company on the seeded chart's shape:
 *   share capital 1,00,000 into the bank; a machine 50,000 bought on credit; sales 30,000 in cash;
 *   cost of sales 12,000 paid from the bank; depreciation 5,000.
 * Profit 13,000. Bank 1,18,000 + machine net 45,000 = 1,63,000 = capital 1,00,000 + profit 13,000 + creditors 50,000.
 */
@ExtendWith(MockitoExtension.class)
class FinancialStatementServiceImplTest {

    @Mock private AccountGroupRepository groupRepo;
    @Mock private LedgerAccountRepository ledgerRepo;
    @Mock private VoucherLineRepository voucherLineRepo;

    @InjectMocks private FinancialStatementServiceImpl service;

    private static final LocalDate FY_START = LocalDate.of(2026, 4, 1);
    private static final LocalDate FY_END = LocalDate.of(2027, 3, 31);

    private final List<AccountGroup> groups = new ArrayList<>();
    private final List<LedgerAccount> ledgers = new ArrayList<>();
    private long nextId = 1;

    private LedgerAccount bank, machine, accDep, shareCapital, retained, creditors, sales, cogs, depreciation, rent;

    @BeforeEach
    void chart() {
        AccountGroup assets = group("ASSETS", "Assets", AccountNature.ASSET, null, 10);
        AccountGroup fixed = group("FIXED_ASSETS", "Fixed Assets", AccountNature.ASSET, assets, 11);
        AccountGroup current = group("CURRENT_ASSETS", "Current Assets", AccountNature.ASSET, assets, 12);
        AccountGroup cash = group("CASH_AND_BANK_GRP", "Cash & Cash Equivalents", AccountNature.ASSET, current, 15);
        AccountGroup liab = group("LIABILITIES", "Liabilities", AccountNature.LIABILITY, null, 20);
        AccountGroup curLiab = group("CURRENT_LIAB", "Current Liabilities", AccountNature.LIABILITY, liab, 22);
        AccountGroup equity = group("EQUITY", "Equity", AccountNature.EQUITY, null, 30);
        AccountGroup capital = group("SHARE_CAPITAL_GRP", "Share Capital", AccountNature.EQUITY, equity, 31);
        AccountGroup reserves = group("RESERVES_SURPLUS", "Reserves & Surplus", AccountNature.EQUITY, equity, 32);
        AccountGroup income = group("INCOME", "Income", AccountNature.INCOME, null, 40);
        AccountGroup revenue = group("REVENUE", "Revenue from Operations", AccountNature.INCOME, income, 41);
        AccountGroup expenses = group("EXPENSES", "Expenses", AccountNature.EXPENSE, null, 50);
        AccountGroup direct = group("DIRECT_EXPENSES", "Direct Expenses", AccountNature.EXPENSE, expenses, 52);
        AccountGroup admin = group("ADMIN_GENERAL", "Administrative Expenses", AccountNature.EXPENSE, expenses, 54);
        AccountGroup dep = group("DEPRECIATION_GRP", "Depreciation & Amortisation", AccountNature.EXPENSE, expenses, 57);

        machine = ledger("1010", "Plant & Machinery", fixed);
        accDep = ledger("1020", "Accumulated Depreciation", fixed);
        bank = ledger("4011", "Bank Account - Primary", cash);
        shareCapital = ledger("3010E", "Share Capital", capital);
        retained = ledger("3020E", "Retained Earnings", reserves);
        creditors = ledger("8010", "Sundry Creditors", curLiab);
        sales = ledger("4010I", "Sales - Domestic", revenue);
        cogs = ledger("5020E", "COGS / Cost of Production", direct);
        rent = ledger("5040E", "Rent", admin);              // never used — must not appear
        depreciation = ledger("5070E", "Depreciation", dep);

        lenient().when(groupRepo.findAllWithParent()).thenReturn(groups);
        lenient().when(ledgerRepo.findAllWithGroupOrderByCode()).thenReturn(ledgers);
    }

    /** The year's postings as Object[]{accountId, sumDr, sumCr}, optionally with the year-end CLOSING voucher. */
    private List<Object[]> yearRows(boolean includeClosing) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(row(bank, "130000", "12000"));
        rows.add(row(machine, "50000", "0"));
        rows.add(row(accDep, "0", "5000"));
        rows.add(row(shareCapital, "0", "100000"));
        rows.add(row(creditors, "0", "50000"));
        if (includeClosing) {   // closing: Dr Sales 30,000 / Cr COGS 12,000, Cr Depreciation 5,000, Cr Retained 13,000
            rows.add(row(sales, "30000", "30000"));
            rows.add(row(cogs, "12000", "12000"));
            rows.add(row(depreciation, "5000", "5000"));
            rows.add(row(retained, "0", "13000"));
        } else {
            rows.add(row(sales, "0", "30000"));
            rows.add(row(cogs, "12000", "0"));
            rows.add(row(depreciation, "5000", "0"));
        }
        return rows;
    }

    @Test
    void balanceSheet_beforeClose_balancesWithProfitBesideRetainedEarnings() {
        when(voucherLineRepo.aggregateByAccount(FY_END)).thenReturn(yearRows(false));
        when(voucherLineRepo.aggregateByAccount(FY_END.minusYears(1))).thenReturn(List.of());

        BalanceSheetDto bs = service.balanceSheet(FY_END, null);

        assertThat(bs.isBalanced()).isTrue();
        assertThat(bs.getTotalAssets()).isEqualByComparingTo("163000");
        assertThat(bs.getTotalEquityAndLiabilities()).isEqualByComparingTo("163000");
        assertThat(bs.getUnclosedProfit()).isEqualByComparingTo("13000");
        assertThat(bs.getPreviousTotalAssets()).isEqualByComparingTo("0");

        StatementLineDto reserves = child(bs.getEquity(), "RESERVES_SURPLUS");
        StatementLineDto surplus = child(reserves, FinancialStatementServiceImpl.UNCLOSED_PROFIT_CODE);
        assertThat(surplus.getKind()).isEqualTo(StatementLineDto.KIND_COMPUTED);
        assertThat(surplus.getAmount()).isEqualByComparingTo("13000");
        assertThat(reserves.getChildren()).hasSize(1);    // Retained Earnings has no balance yet — omitted

        StatementLineDto fixed = child(bs.getAssets(), "FIXED_ASSETS");
        assertThat(fixed.getAmount()).isEqualByComparingTo("45000");
        assertThat(child(fixed, "1020").getAmount()).isEqualByComparingTo("-5000");   // contra asset reads negative
        assertThat(child(fixed, "1020").getAccountId()).isEqualTo(accDep.getId());
        assertThat(child(bs.getLiabilities(), "CURRENT_LIAB").getAmount()).isEqualByComparingTo("50000");
    }

    @Test
    void balanceSheet_afterClose_profitSitsInRetainedEarnings() {
        when(voucherLineRepo.aggregateByAccount(FY_END)).thenReturn(yearRows(true));
        when(voucherLineRepo.aggregateByAccount(FY_END.minusYears(1))).thenReturn(List.of());

        BalanceSheetDto bs = service.balanceSheet(FY_END, null);

        assertThat(bs.isBalanced()).isTrue();
        assertThat(bs.getUnclosedProfit()).isEqualByComparingTo("0");
        StatementLineDto reserves = child(bs.getEquity(), "RESERVES_SURPLUS");
        assertThat(reserves.getChildren()).extracting(StatementLineDto::getCode).containsExactly("3020E");
        assertThat(reserves.getAmount()).isEqualByComparingTo("13000");
        assertThat(bs.getTotalEquityAndLiabilities()).isEqualByComparingTo("163000");
    }

    @Test
    void balanceSheet_unbalancedLedgerOpening_reportsDifference() {
        machine.setOpeningBalance(new BigDecimal("1000"));
        machine.setOpeningBalanceDrCr("DR");
        when(voucherLineRepo.aggregateByAccount(FY_END)).thenReturn(yearRows(false));
        when(voucherLineRepo.aggregateByAccount(FY_END.minusYears(1))).thenReturn(List.of());

        BalanceSheetDto bs = service.balanceSheet(FY_END, null);

        assertThat(bs.isBalanced()).isFalse();
        assertThat(bs.getDifference()).isEqualByComparingTo("1000");
        assertThat(bs.getPreviousDifference()).isEqualByComparingTo("1000");   // a typed opening sits before every date
    }

    @Test
    void profitAndLoss_leavesOutTheClosingVoucher_andGroupsBySection() {
        // The repository is asked to exclude CLOSING, so the movement it returns is the trading only.
        when(voucherLineRepo.aggregateByAccountInRange(eq(FY_START), eq(FY_END), eq(VoucherType.CLOSING)))
                .thenReturn(List.of(row(sales, "0", "30000"), row(cogs, "12000", "0"), row(depreciation, "5000", "0"),
                        row(bank, "30000", "12000")));
        when(voucherLineRepo.aggregateByAccountInRange(eq(FY_START.minusYears(1)), eq(FY_END.minusYears(1)), eq(VoucherType.CLOSING)))
                .thenReturn(List.<Object[]>of(row(sales, "0", "8000"), row(cogs, "3000", "0")));

        ProfitLossDto pl = service.profitAndLoss(FY_START, FY_END, null, null);

        assertThat(pl.getTotalIncome()).isEqualByComparingTo("30000");
        assertThat(pl.getTotalExpenses()).isEqualByComparingTo("17000");
        assertThat(pl.getNetProfit()).isEqualByComparingTo("13000");
        assertThat(pl.getPreviousNetProfit()).isEqualByComparingTo("5000");
        assertThat(pl.getPreviousFrom()).isEqualTo(FY_START.minusYears(1));

        assertThat(pl.getExpenses().getChildren()).extracting(StatementLineDto::getCode)
                .containsExactly("DIRECT_EXPENSES", "DEPRECIATION_GRP");             // Rent / Admin had nothing — omitted
        StatementLineDto direct = child(pl.getExpenses(), "DIRECT_EXPENSES");
        assertThat(direct.getPreviousAmount()).isEqualByComparingTo("3000");
        assertThat(child(pl.getIncome(), "REVENUE").getAmount()).isEqualByComparingTo("30000");
    }

    @Test
    void profitAndLoss_fromAfterTo_refused() {
        assertThatThrownBy(() -> service.profitAndLoss(FY_END, FY_START, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void excel_writesBothStatements() throws Exception {
        when(voucherLineRepo.aggregateByAccount(FY_END)).thenReturn(yearRows(false));
        when(voucherLineRepo.aggregateByAccount(FY_END.minusYears(1))).thenReturn(List.of());

        byte[] bytes = service.balanceSheetExcel(service.balanceSheet(FY_END, null));

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheet("Balance Sheet")).isNotNull();
            boolean foundSurplus = false;
            for (var row : wb.getSheet("Balance Sheet")) {
                if (row.getCell(0) != null && FinancialStatementServiceImpl.UNCLOSED_PROFIT_NAME.equals(row.getCell(0).getStringCellValue())) {
                    foundSurplus = true;
                    assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(13000.0);
                }
            }
            assertThat(foundSurplus).isTrue();
        }
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private AccountGroup group(String code, String name, AccountNature nature, AccountGroup parent, int sort) {
        AccountGroup g = new AccountGroup();
        g.setId(nextId++);
        g.setCode(code);
        g.setName(name);
        g.setNature(nature);
        g.setParentGroup(parent);
        g.setSortOrder(sort);
        groups.add(g);
        return g;
    }

    private LedgerAccount ledger(String code, String name, AccountGroup group) {
        LedgerAccount la = new LedgerAccount();
        la.setId(nextId++);
        la.setCode(code);
        la.setName(name);
        la.setGroup(group);
        la.setNature(group.getNature());
        ledgers.add(la);
        return la;
    }

    private static Object[] row(LedgerAccount la, String dr, String cr) {
        return new Object[]{la.getId(), new BigDecimal(dr), new BigDecimal(cr)};
    }

    private static StatementLineDto child(StatementLineDto parent, String code) {
        Optional<StatementLineDto> c = parent.getChildren().stream().filter(l -> code.equals(l.getCode())).findFirst();
        assertThat(c).as("child " + code + " of " + parent.getCode()).isPresent();
        return c.get();
    }
}
