package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.posting.LedgerResolver;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.StockGlReconciliationDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.StockGlReconciliationRowDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.items.model.ItemType;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proves perpetual inventory ties to the books: compares each stock GL account balance (built from
 * vouchers) with an independent valuation replayed from the {@code InventoryLedger}. The replay uses
 * the same allowlist/mapping as {@code InventoryPostingListener}, so the two agree unless a movement
 * failed to post (or predates Phase 3) — exactly the condition the report exists to surface.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockGlReconciliationServiceImpl implements StockGlReconciliationService {

    private static final BigDecimal TOLERANCE = BigDecimal.ONE;   // ₹1
    private static final String REF_WORK_ORDER = "WORK_ORDER";
    private static final List<String> STOCK_CODES =
            List.of(LedgerResolver.RAW_MATERIAL_STOCK, LedgerResolver.WIP_STOCK, LedgerResolver.FINISHED_GOODS_STOCK);

    private final VoucherLineRepository voucherLineRepo;
    private final InventoryLedgerRepository inventoryLedgerRepo;
    private final LedgerAccountRepository ledgerRepo;

    @Override
    public StockGlReconciliationDto reconcile(LocalDate from, LocalDate asOf) {
        // 1. GL side: net Dr − Cr for the stock accounts + GR/IR clearing. Windowed to the cutover
        //    when `from` is set (so pre-migration postings are excluded), else cumulative as-of asOf.
        List<String> codes = new ArrayList<>(STOCK_CODES);
        codes.add(LedgerResolver.GR_IR_CLEARING);
        List<Object[]> glRows = (from != null)
                ? voucherLineRepo.movementByCodeInRange(from, asOf, codes)
                : voucherLineRepo.balanceByCodeAsOf(asOf, codes);
        Map<String, BigDecimal> glBalance = new HashMap<>();
        for (Object[] r : glRows) {
            BigDecimal dr = nz((BigDecimal) r[1]);
            BigDecimal cr = nz((BigDecimal) r[2]);
            glBalance.put((String) r[0], dr.subtract(cr));
        }

        // 2. Independent stock valuation replayed from the inventory ledger over the same window.
        Map<String, BigDecimal> stockValue = new HashMap<>();
        List<Object[]> movements = (from != null)
                ? inventoryLedgerRepo.movementsForReconciliationInRange(from, asOf)
                : inventoryLedgerRepo.movementsForReconciliation(asOf);
        for (Object[] m : movements) {
            applyMovement(stockValue,
                    (String) m[0], (String) m[1],
                    m[2] != null ? ((Number) m[2]).doubleValue() : 0.0,
                    m[3] != null ? ((Number) m[3]).doubleValue() : 0.0,
                    (ItemType) m[4]);
        }

        // 3. Build one row per stock account.
        Map<String, String> names = stockAccountNames();
        List<StockGlReconciliationRowDto> rows = new ArrayList<>();
        boolean allTie = true;
        for (String code : STOCK_CODES) {
            BigDecimal gl = scale(glBalance.getOrDefault(code, BigDecimal.ZERO));
            BigDecimal stock = scale(stockValue.getOrDefault(code, BigDecimal.ZERO));
            BigDecimal variance = gl.subtract(stock);
            boolean ties = variance.abs().compareTo(TOLERANCE) <= 0;
            allTie = allTie && ties;
            rows.add(new StockGlReconciliationRowDto(code, names.getOrDefault(code, code), stock, gl, variance, ties));
        }

        // GR/IR net credit (Cr − Dr) = goods received but not yet invoiced.
        BigDecimal grIr = scale(glBalance.getOrDefault(LedgerResolver.GR_IR_CLEARING, BigDecimal.ZERO)).negate();
        return new StockGlReconciliationDto(asOf, rows, grIr, allTie);
    }

    /** Applies one valued movement to the running stock valuation, mirroring the posting allowlist. */
    private void applyMovement(Map<String, BigDecimal> acc, String txn, String refType,
                               double qty, double amount, ItemType itemType) {
        BigDecimal amt = BigDecimal.valueOf(Math.abs(amount)).setScale(2, RoundingMode.HALF_UP);
        if (amt.signum() == 0 || txn == null) return;
        boolean wo = REF_WORK_ORDER.equals(refType);
        String stock = (itemType == ItemType.FINISHED_GOOD || itemType == ItemType.SEMI_FINISHED)
                ? LedgerResolver.FINISHED_GOODS_STOCK : LedgerResolver.RAW_MATERIAL_STOCK;
        switch (txn) {
            case "GRN" -> add(acc, stock, amt);
            case "CONSUME" -> { if (wo) { add(acc, LedgerResolver.WIP_STOCK, amt); add(acc, stock, amt.negate()); } }
            case "PRODUCE" -> { if (wo) { add(acc, stock, amt); add(acc, LedgerResolver.WIP_STOCK, amt.negate()); } }
            case "SALES_DISPATCH" -> add(acc, stock, amt.negate());
            case "RETURN" -> { if (wo) { add(acc, stock, amt); add(acc, LedgerResolver.WIP_STOCK, amt.negate()); } }
            case "ADJUSTMENT" -> add(acc, stock, qty >= 0 ? amt : amt.negate());
            default -> { /* RESERVE, ISSUE, non-WO consume/produce — not a value-changing GL movement */ }
        }
    }

    private void add(Map<String, BigDecimal> acc, String code, BigDecimal v) {
        acc.merge(code, v, BigDecimal::add);
    }

    private Map<String, String> stockAccountNames() {
        Map<String, String> names = new HashMap<>();
        for (String code : STOCK_CODES) {
            ledgerRepo.findByCodeAndDeletedDateIsNull(code).ifPresent(la -> names.put(code, la.getName()));
        }
        return names;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal scale(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    // ─── Excel ───────────────────────────────────────────────────────────────

    @Override
    public byte[] toExcel(StockGlReconciliationDto report) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Stock vs GL");

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            int r = 0;
            sheet.createRow(r++).createCell(0).setCellValue("Stock vs GL Reconciliation as of " + report.getAsOf());
            r++; // spacer

            String[] columns = {"Code", "Account", "Stock Value (Ledger)", "GL Balance", "Variance", "Ties?"};
            Row header = sheet.createRow(r++);
            for (int i = 0; i < columns.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(columns[i]);
                c.setCellStyle(headerStyle);
            }

            for (StockGlReconciliationRowDto row : report.getRows()) {
                Row er = sheet.createRow(r++);
                er.createCell(0).setCellValue(row.getCode());
                er.createCell(1).setCellValue(row.getName() != null ? row.getName() : "");
                er.createCell(2).setCellValue(dbl(row.getStockValue()));
                er.createCell(3).setCellValue(dbl(row.getGlBalance()));
                er.createCell(4).setCellValue(dbl(row.getVariance()));
                er.createCell(5).setCellValue(row.isTiesOut() ? "Yes" : "No");
            }

            r++; // spacer
            Row grIr = sheet.createRow(r++);
            Cell label = grIr.createCell(0);
            label.setCellValue("GR/IR Clearing (goods received not invoiced)");
            label.setCellStyle(headerStyle);
            grIr.createCell(3).setCellValue(dbl(report.getGrIrBalance()));

            for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate stock-GL reconciliation Excel", e);
        }
    }

    private double dbl(BigDecimal v) {
        return v != null ? v.doubleValue() : 0.0;
    }
}
