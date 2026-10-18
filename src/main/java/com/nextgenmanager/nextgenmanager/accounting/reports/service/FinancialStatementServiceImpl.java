package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountGroup;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.AccountNature;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.AccountGroupRepository;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.posting.LedgerResolver;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.BalanceSheetDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.ProfitLossDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.StatementLineDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Profit & Loss and Balance Sheet laid out by the chart of accounts' group tree: each top-level
 * group of a nature is one side of the statement, its sub-groups are the Schedule III heads, and
 * ledgers are the leaves. Nothing is keyed on ledger codes, so a group or ledger a user adds lands
 * under its parent without code changes. Only the profit not yet closed is placed by code — beside
 * Retained Earnings.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialStatementServiceImpl implements FinancialStatementService {

    static final String UNCLOSED_PROFIT_CODE = "P&L";
    static final String UNCLOSED_PROFIT_NAME = "Surplus in Profit & Loss (not yet closed)";
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final AccountGroupRepository groupRepo;
    private final LedgerAccountRepository ledgerRepo;
    private final VoucherLineRepository voucherLineRepo;

    // ─── Profit & Loss ───────────────────────────────────────────────────────

    @Override
    public ProfitLossDto profitAndLoss(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {
        if (from.isAfter(to)) throw new IllegalArgumentException("'from' must not be after 'to'");
        LocalDate pFrom = previousFrom != null ? previousFrom : from.minusYears(1);
        LocalDate pTo = previousTo != null ? previousTo : to.minusYears(1);

        Chart chart = loadChart();
        Map<Long, BigDecimal> cur = netByAccount(voucherLineRepo.aggregateByAccountInRange(from, to, VoucherType.CLOSING));
        Map<Long, BigDecimal> prev = netByAccount(voucherLineRepo.aggregateByAccountInRange(pFrom, pTo, VoucherType.CLOSING));

        StatementLineDto income = chart.side(AccountNature.INCOME, "Income", cur, prev, Map.of());
        StatementLineDto expenses = chart.side(AccountNature.EXPENSE, "Expenses", cur, prev, Map.of());

        ProfitLossDto dto = new ProfitLossDto();
        dto.setFrom(from);
        dto.setTo(to);
        dto.setPreviousFrom(pFrom);
        dto.setPreviousTo(pTo);
        dto.setIncome(income);
        dto.setExpenses(expenses);
        dto.setTotalIncome(income.getAmount());
        dto.setPreviousTotalIncome(income.getPreviousAmount());
        dto.setTotalExpenses(expenses.getAmount());
        dto.setPreviousTotalExpenses(expenses.getPreviousAmount());
        dto.setNetProfit(income.getAmount().subtract(expenses.getAmount()));
        dto.setPreviousNetProfit(income.getPreviousAmount().subtract(expenses.getPreviousAmount()));
        return dto;
    }

    // ─── Balance Sheet ───────────────────────────────────────────────────────

    @Override
    public BalanceSheetDto balanceSheet(LocalDate asOf, LocalDate previousAsOf) {
        LocalDate pAsOf = previousAsOf != null ? previousAsOf : asOf.minusYears(1);

        Chart chart = loadChart();
        Map<Long, BigDecimal> cur = withLedgerOpenings(chart, netByAccount(voucherLineRepo.aggregateByAccount(asOf)));
        Map<Long, BigDecimal> prev = withLedgerOpenings(chart, netByAccount(voucherLineRepo.aggregateByAccount(pAsOf)));

        // Income and expense balances still open (all of them before a close, none after) are the
        // profit not yet moved to Retained Earnings: Σ(Cr − Dr) over both natures = −Σ(Dr − Cr).
        BigDecimal unclosed = chart.unclosedProfit(cur);
        BigDecimal prevUnclosed = chart.unclosedProfit(prev);

        Map<Long, List<StatementLineDto>> extras = new HashMap<>();
        Long surplusGroup = chart.groupOfLedger(LedgerResolver.RETAINED_EARNINGS, AccountNature.EQUITY);
        StatementLineDto surplus = null;
        if (unclosed.signum() != 0 || prevUnclosed.signum() != 0) {
            surplus = new StatementLineDto(StatementLineDto.KIND_COMPUTED, UNCLOSED_PROFIT_CODE, UNCLOSED_PROFIT_NAME);
            surplus.setAmount(unclosed);
            surplus.setPreviousAmount(prevUnclosed);
            if (surplusGroup != null) extras.put(surplusGroup, List.of(surplus));
        }

        StatementLineDto equity = chart.side(AccountNature.EQUITY, "Equity", cur, prev, extras);
        if (surplus != null && surplusGroup == null) {   // no Retained Earnings ledger — sit at the top of equity
            equity.getChildren().add(surplus);
            equity.setAmount(equity.getAmount().add(surplus.getAmount()));
            equity.setPreviousAmount(equity.getPreviousAmount().add(surplus.getPreviousAmount()));
        }
        StatementLineDto liabilities = chart.side(AccountNature.LIABILITY, "Liabilities", cur, prev, Map.of());
        StatementLineDto assets = chart.side(AccountNature.ASSET, "Assets", cur, prev, Map.of());

        BigDecimal eqLiab = equity.getAmount().add(liabilities.getAmount());
        BigDecimal prevEqLiab = equity.getPreviousAmount().add(liabilities.getPreviousAmount());

        BalanceSheetDto dto = new BalanceSheetDto();
        dto.setAsOf(asOf);
        dto.setPreviousAsOf(pAsOf);
        dto.setEquity(equity);
        dto.setLiabilities(liabilities);
        dto.setAssets(assets);
        dto.setTotalEquityAndLiabilities(eqLiab);
        dto.setPreviousTotalEquityAndLiabilities(prevEqLiab);
        dto.setTotalAssets(assets.getAmount());
        dto.setPreviousTotalAssets(assets.getPreviousAmount());
        dto.setUnclosedProfit(unclosed);
        dto.setPreviousUnclosedProfit(prevUnclosed);
        dto.setDifference(assets.getAmount().subtract(eqLiab));
        dto.setPreviousDifference(assets.getPreviousAmount().subtract(prevEqLiab));
        dto.setBalanced(dto.getDifference().abs().compareTo(TOLERANCE) < 0
                && dto.getPreviousDifference().abs().compareTo(TOLERANCE) < 0);
        return dto;
    }

    // ─── Chart of accounts as a tree ─────────────────────────────────────────

    private Chart loadChart() {
        return new Chart(groupRepo.findAllWithParent(), ledgerRepo.findAllWithGroupOrderByCode());
    }

    /** Opening balances typed straight onto a ledger (not via an OPENING voucher) count, as on the trial balance. */
    private Map<Long, BigDecimal> withLedgerOpenings(Chart chart, Map<Long, BigDecimal> net) {
        Map<Long, BigDecimal> out = new HashMap<>(net);
        for (LedgerAccount la : chart.ledgers) {
            BigDecimal ob = la.getOpeningBalance();
            if (ob == null || ob.signum() == 0) continue;
            out.merge(la.getId(), "DR".equals(la.getOpeningBalanceDrCr()) ? ob : ob.negate(), BigDecimal::add);
        }
        return out;
    }

    /** Object[]{accountId, sumDr, sumCr} → accountId → Dr − Cr. */
    private static Map<Long, BigDecimal> netByAccount(List<Object[]> rows) {
        Map<Long, BigDecimal> net = new HashMap<>();
        for (Object[] r : rows) {
            net.put((Long) r[0], nz((BigDecimal) r[1]).subtract(nz((BigDecimal) r[2])));
        }
        return net;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** The group tree, its ledgers, and the nature of the top-level group each group sits under. */
    private static final class Chart {
        private static final Comparator<AccountGroup> GROUP_ORDER =
                Comparator.comparingInt(AccountGroup::getSortOrder).thenComparing(AccountGroup::getName);

        private final Map<Long, AccountGroup> groups = new LinkedHashMap<>();
        private final Map<Long, List<AccountGroup>> childGroups = new HashMap<>();
        private final Map<Long, List<LedgerAccount>> ledgersByGroup = new HashMap<>();
        private final List<AccountGroup> roots = new ArrayList<>();
        private final Map<Long, AccountNature> rootNature = new HashMap<>();
        private final List<LedgerAccount> ledgers;

        Chart(List<AccountGroup> allGroups, List<LedgerAccount> allLedgers) {
            for (AccountGroup g : allGroups) groups.put(g.getId(), g);
            for (AccountGroup g : allGroups) {
                Long parentId = g.getParentGroup() != null ? g.getParentGroup().getId() : null;
                if (parentId == null || !groups.containsKey(parentId)) roots.add(g);   // a deleted parent promotes the child
                else childGroups.computeIfAbsent(parentId, k -> new ArrayList<>()).add(g);
            }
            roots.sort(GROUP_ORDER);
            childGroups.values().forEach(l -> l.sort(GROUP_ORDER));
            for (AccountGroup root : roots) markNature(root, root.getNature());

            this.ledgers = allLedgers.stream().filter(la -> groups.containsKey(la.getGroup().getId())).toList();
            for (LedgerAccount la : ledgers) {
                ledgersByGroup.computeIfAbsent(la.getGroup().getId(), k -> new ArrayList<>()).add(la);
            }
        }

        private void markNature(AccountGroup g, AccountNature nature) {
            rootNature.put(g.getId(), nature);
            for (AccountGroup child : childGroups.getOrDefault(g.getId(), List.of())) markNature(child, nature);
        }

        Long groupOfLedger(String code, AccountNature nature) {
            return ledgers.stream()
                    .filter(la -> code.equals(la.getCode()))
                    .map(la -> la.getGroup().getId())
                    .filter(gid -> rootNature.get(gid) == nature)
                    .findFirst().orElse(null);
        }

        BigDecimal unclosedProfit(Map<Long, BigDecimal> net) {
            BigDecimal drMinusCr = BigDecimal.ZERO;
            for (LedgerAccount la : ledgers) {
                AccountNature n = rootNature.get(la.getGroup().getId());
                if (n == AccountNature.INCOME || n == AccountNature.EXPENSE) {
                    drMinusCr = drMinusCr.add(net.getOrDefault(la.getId(), BigDecimal.ZERO));
                }
            }
            return money(drMinusCr.negate());
        }

        /**
         * One side of a statement: every top-level group of the nature. A single root (the seeded
         * chart) is the side itself; several are wrapped under one heading.
         */
        StatementLineDto side(AccountNature nature, String title,
                              Map<Long, BigDecimal> cur, Map<Long, BigDecimal> prev,
                              Map<Long, List<StatementLineDto>> extras) {
            boolean drPositive = nature == AccountNature.ASSET || nature == AccountNature.EXPENSE;
            List<AccountGroup> natureRoots = roots.stream().filter(r -> r.getNature() == nature).toList();

            StatementLineDto top;
            if (natureRoots.size() == 1) {
                top = groupNode(natureRoots.get(0), drPositive, cur, prev, extras);
                if (top == null) top = new StatementLineDto(StatementLineDto.KIND_GROUP, natureRoots.get(0).getCode(), natureRoots.get(0).getName());
            } else {
                top = new StatementLineDto(StatementLineDto.KIND_GROUP, nature.name(), title);
                for (AccountGroup root : natureRoots) {
                    StatementLineDto node = groupNode(root, drPositive, cur, prev, extras);
                    if (node != null) addChild(top, node);
                }
            }
            return top;
        }

        /** A group with its non-zero descendants, or null when nothing under it has a balance in either column. */
        private StatementLineDto groupNode(AccountGroup g, boolean drPositive,
                                          Map<Long, BigDecimal> cur, Map<Long, BigDecimal> prev,
                                          Map<Long, List<StatementLineDto>> extras) {
            StatementLineDto node = new StatementLineDto(StatementLineDto.KIND_GROUP, g.getCode(), g.getName());
            for (AccountGroup child : childGroups.getOrDefault(g.getId(), List.of())) {
                StatementLineDto c = groupNode(child, drPositive, cur, prev, extras);
                if (c != null) addChild(node, c);
            }
            for (LedgerAccount la : ledgersByGroup.getOrDefault(g.getId(), List.of())) {
                BigDecimal a = signed(cur.get(la.getId()), drPositive);
                BigDecimal p = signed(prev.get(la.getId()), drPositive);
                if (a.signum() == 0 && p.signum() == 0) continue;
                StatementLineDto leaf = new StatementLineDto(StatementLineDto.KIND_LEDGER, la.getCode(), la.getName());
                leaf.setAccountId(la.getId());
                leaf.setAmount(a);
                leaf.setPreviousAmount(p);
                addChild(node, leaf);
            }
            for (StatementLineDto extra : extras.getOrDefault(g.getId(), List.of())) addChild(node, extra);
            return node.getChildren().isEmpty() ? null : node;
        }

        private static void addChild(StatementLineDto parent, StatementLineDto child) {
            parent.getChildren().add(child);
            parent.setAmount(parent.getAmount().add(child.getAmount()));
            parent.setPreviousAmount(parent.getPreviousAmount().add(child.getPreviousAmount()));
        }

        private static BigDecimal signed(BigDecimal drMinusCr, boolean drPositive) {
            BigDecimal v = money(drMinusCr);
            return drPositive ? v : v.negate();
        }
    }

    // ─── Excel ───────────────────────────────────────────────────────────────

    @Override
    public byte[] profitAndLossExcel(ProfitLossDto r) {
        return writeStatement("Profit & Loss",
                "Statement of Profit and Loss for " + DMY.format(r.getFrom()) + " to " + DMY.format(r.getTo()),
                DMY.format(r.getFrom()) + " – " + DMY.format(r.getTo()),
                DMY.format(r.getPreviousFrom()) + " – " + DMY.format(r.getPreviousTo()),
                List.of(r.getIncome(), r.getExpenses()),
                List.of(new Object[]{"Total Income", r.getTotalIncome(), r.getPreviousTotalIncome()},
                        new Object[]{"Total Expenses", r.getTotalExpenses(), r.getPreviousTotalExpenses()},
                        new Object[]{"Net Profit / (Loss)", r.getNetProfit(), r.getPreviousNetProfit()}));
    }

    @Override
    public byte[] balanceSheetExcel(BalanceSheetDto r) {
        List<Object[]> totals = new ArrayList<>();
        totals.add(new Object[]{"Total Equity & Liabilities", r.getTotalEquityAndLiabilities(), r.getPreviousTotalEquityAndLiabilities()});
        totals.add(new Object[]{"Total Assets", r.getTotalAssets(), r.getPreviousTotalAssets()});
        if (!r.isBalanced()) {
            totals.add(new Object[]{"Difference (books do not balance)", r.getDifference(), r.getPreviousDifference()});
        }
        return writeStatement("Balance Sheet",
                "Balance Sheet as at " + DMY.format(r.getAsOf()),
                "As at " + DMY.format(r.getAsOf()),
                "As at " + DMY.format(r.getPreviousAsOf()),
                List.of(r.getEquity(), r.getLiabilities(), r.getAssets()),
                totals);
    }

    private byte[] writeStatement(String sheetName, String title, String curLabel, String prevLabel,
                                  List<StatementLineDto> sides, List<Object[]> totals) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            Font bold = wb.createFont();
            bold.setBold(true);
            short moneyFmt = wb.createDataFormat().getFormat("#,##0.00;(#,##0.00)");

            CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(bold);
            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(moneyFmt);
            CellStyle moneyBold = wb.createCellStyle();
            moneyBold.setDataFormat(moneyFmt);
            moneyBold.setFont(bold);
            Map<String, CellStyle> labelStyles = new HashMap<>();

            int r = 0;
            Cell t = sheet.createRow(r++).createCell(0);
            t.setCellValue(title);
            t.setCellStyle(boldStyle);
            r++;

            Row header = sheet.createRow(r++);
            String[] cols = {"Particulars", curLabel, prevLabel};
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(boldStyle);
            }

            for (StatementLineDto side : sides) {
                r = writeLine(wb, sheet, r, side, 0, bold, labelStyles, moneyStyle, moneyBold);
                r++;
            }
            for (Object[] total : totals) {
                Row row = sheet.createRow(r++);
                Cell label = row.createCell(0);
                label.setCellValue((String) total[0]);
                label.setCellStyle(boldStyle);
                moneyCell(row, 1, (BigDecimal) total[1], moneyBold);
                moneyCell(row, 2, (BigDecimal) total[2], moneyBold);
            }

            sheet.setColumnWidth(0, 60 * 256);
            sheet.setColumnWidth(1, 22 * 256);
            sheet.setColumnWidth(2, 22 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate " + sheetName + " Excel", e);
        }
    }

    private int writeLine(Workbook wb, Sheet sheet, int r, StatementLineDto line, int depth, Font bold,
                          Map<String, CellStyle> labelStyles, CellStyle moneyStyle, CellStyle moneyBold) {
        boolean group = StatementLineDto.KIND_GROUP.equals(line.getKind());
        Row row = sheet.createRow(r++);
        Cell label = row.createCell(0);
        label.setCellValue(line.getName());
        label.setCellStyle(labelStyles.computeIfAbsent(depth + ":" + group, k -> {
            CellStyle s = wb.createCellStyle();
            s.setIndention((short) Math.min(depth * 2, 15));
            if (group) s.setFont(bold);
            return s;
        }));
        moneyCell(row, 1, line.getAmount(), group ? moneyBold : moneyStyle);
        moneyCell(row, 2, line.getPreviousAmount(), group ? moneyBold : moneyStyle);
        for (StatementLineDto child : line.getChildren()) {
            r = writeLine(wb, sheet, r, child, depth + 1, bold, labelStyles, moneyStyle, moneyBold);
        }
        return r;
    }

    private void moneyCell(Row row, int col, BigDecimal v, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(v != null ? v.doubleValue() : 0.0);
        c.setCellStyle(style);
    }
}
