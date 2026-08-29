package com.nextgenmanager.nextgenmanager.accounting.reports.service;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.SubLedgerType;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.opening.model.OpeningBalance;
import com.nextgenmanager.nextgenmanager.accounting.opening.repository.OpeningBalanceRepository;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.AgeingReportDto;
import com.nextgenmanager.nextgenmanager.accounting.reports.dto.AgeingRowDto;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherType;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgeingReportServiceImpl implements AgeingReportService {

    private final VoucherLineRepository voucherLineRepo;
    private final LedgerAccountRepository ledgerRepo;
    private final OpeningBalanceRepository openingBalanceRepo;

    @Override
    public AgeingReportDto debtorsAgeing(LocalDate asOf) {
        return ageing(SubLedgerType.CUSTOMER, asOf);
    }

    @Override
    public AgeingReportDto creditorsAgeing(LocalDate asOf) {
        return ageing(SubLedgerType.VENDOR, asOf);
    }

    // ─── Core FIFO open-item ageing ──────────────────────────────────────────

    private AgeingReportDto ageing(SubLedgerType type, LocalDate asOf) {
        boolean debtors = type == SubLedgerType.CUSTOMER;

        Map<Long, LedgerAccount> meta = ledgerRepo.findBySubLedgerTypeAndDeletedDateIsNull(type)
                .stream().collect(Collectors.toMap(LedgerAccount::getId, Function.identity()));

        // Lines come back ordered by ledger then date; group preserving order.
        Map<Long, List<Object[]>> byLedger = new LinkedHashMap<>();
        for (Object[] r : voucherLineRepo.partyLedgerLines(type, asOf)) {
            byLedger.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add(r);
        }

        // Cutover open items (V162), each carrying its ORIGINAL bill date. Where a party has these,
        // they replace that party's OPENING voucher line — the line is their sum, so counting both
        // would double the balance. Parties without them age off the voucher date exactly as before.
        Map<Long, List<OpeningBalance>> openingItems = new LinkedHashMap<>();
        for (OpeningBalance ob : openingBalanceRepo.datedOpenItems(type, asOf)) {
            openingItems.computeIfAbsent(ob.getLedgerAccount().getId(), k -> new ArrayList<>()).add(ob);
        }

        List<AgeingRowDto> rows = new ArrayList<>();
        for (Map.Entry<Long, List<Object[]>> e : byLedger.entrySet()) {
            AgeingRowDto row = computeParty(e.getKey(), e.getValue(),
                    openingItems.getOrDefault(e.getKey(), List.of()),
                    asOf, debtors, meta.get(e.getKey()));
            if (row.getTotal().signum() != 0) rows.add(row);   // hide fully-settled parties
        }
        rows.sort(Comparator.comparing(AgeingRowDto::getTotal).reversed());

        return new AgeingReportDto(debtors ? "DEBTORS" : "CREDITORS", asOf, rows, totals(rows));
    }

    /** Mutable open charge for FIFO allocation. */
    private static final class OpenItem {
        final LocalDate date;
        BigDecimal remaining;
        OpenItem(LocalDate date, BigDecimal remaining) { this.date = date; this.remaining = remaining; }
    }

    private AgeingRowDto computeParty(Long ledgerId, List<Object[]> lines,
                                      List<OpeningBalance> cutoverItems, LocalDate asOf,
                                      boolean debtors, LedgerAccount meta) {
        LinkedList<OpenItem> open = new LinkedList<>();
        BigDecimal advance = BigDecimal.ZERO;   // payments not yet matched to a charge

        // Seed the FIFO queue with the carried-over bills at their real dates, oldest first, so a
        // three-year-old invoice lands in 90+ on day one instead of reading as "current".
        // A cutover row on the opposite side (an unadjusted advance) seeds the advance pool.
        boolean seeded = !cutoverItems.isEmpty();
        for (OpeningBalance ob : cutoverItems) {
            boolean isCharge = debtors == "DR".equalsIgnoreCase(ob.getDrCr());
            if (isCharge) open.add(new OpenItem(ob.getBillDate(), nz(ob.getAmount())));
            else          advance = advance.add(nz(ob.getAmount()));
        }
        open.sort(Comparator.comparing(oi -> oi.date));

        for (Object[] l : lines) {
            // The OPENING line is the sum of the rows just seeded — skip it or the party doubles.
            if (seeded && VoucherType.OPENING == l[4]) continue;
            LocalDate date = (LocalDate) l[1];
            BigDecimal dr = nz((BigDecimal) l[2]);
            BigDecimal cr = nz((BigDecimal) l[3]);
            // For debtors a debit raises the balance (a charge); for creditors a credit does.
            BigDecimal charge  = debtors ? dr : cr;
            BigDecimal payment = debtors ? cr : dr;

            if (charge.signum() > 0) {
                if (advance.signum() > 0) {                 // soak up earlier overpayment first
                    BigDecimal applied = advance.min(charge);
                    charge = charge.subtract(applied);
                    advance = advance.subtract(applied);
                }
                if (charge.signum() > 0) open.add(new OpenItem(date, charge));
            }
            if (payment.signum() > 0) {
                BigDecimal pay = payment;
                while (pay.signum() > 0 && !open.isEmpty()) {   // FIFO: oldest charge first
                    OpenItem head = open.getFirst();
                    BigDecimal applied = pay.min(head.remaining);
                    head.remaining = head.remaining.subtract(applied);
                    pay = pay.subtract(applied);
                    if (head.remaining.signum() == 0) open.removeFirst();
                }
                if (pay.signum() > 0) advance = advance.add(pay);   // overpayment / advance
            }
        }

        BigDecimal cur = BigDecimal.ZERO, b1 = BigDecimal.ZERO, b2 = BigDecimal.ZERO, b3 = BigDecimal.ZERO;
        for (OpenItem oi : open) {
            long age = ChronoUnit.DAYS.between(oi.date, asOf);
            if (age <= 30)      cur = cur.add(oi.remaining);
            else if (age <= 60) b1 = b1.add(oi.remaining);
            else if (age <= 90) b2 = b2.add(oi.remaining);
            else                b3 = b3.add(oi.remaining);
        }
        // A net advance shows as a negative current balance so buckets tie to the ledger balance.
        cur = cur.subtract(advance);

        BigDecimal total = cur.add(b1).add(b2).add(b3);
        return new AgeingRowDto(ledgerId,
                meta != null ? meta.getCode() : null,
                meta != null ? meta.getName() : "Ledger " + ledgerId,
                cur, b1, b2, b3, total);
    }

    private AgeingRowDto totals(List<AgeingRowDto> rows) {
        BigDecimal cur = BigDecimal.ZERO, b1 = BigDecimal.ZERO, b2 = BigDecimal.ZERO, b3 = BigDecimal.ZERO, t = BigDecimal.ZERO;
        for (AgeingRowDto r : rows) {
            cur = cur.add(r.getCurrent());
            b1  = b1.add(r.getDays31_60());
            b2  = b2.add(r.getDays61_90());
            b3  = b3.add(r.getDays90Plus());
            t   = t.add(r.getTotal());
        }
        return new AgeingRowDto(null, null, "TOTAL", cur, b1, b2, b3, t);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ─── Excel ───────────────────────────────────────────────────────────────

    @Override
    public byte[] toExcel(AgeingReportDto report) {
        String title = "DEBTORS".equals(report.getType()) ? "Debtors Ageing" : "Creditors Ageing";
        String partyHeader = "DEBTORS".equals(report.getType()) ? "Customer" : "Vendor";
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(title);

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(headerFont);

            int r = 0;
            Row meta = sheet.createRow(r++);
            meta.createCell(0).setCellValue(title + " as of " + report.getAsOf());
            r++; // blank spacer

            String[] columns = {"Code", partyHeader, "Current (0-30)", "31-60 days", "61-90 days", "90+ days", "Total Outstanding"};
            Row header = sheet.createRow(r++);
            for (int i = 0; i < columns.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(columns[i]);
                c.setCellStyle(headerStyle);
            }

            for (AgeingRowDto row : report.getRows()) {
                Row excelRow = sheet.createRow(r++);
                writeRow(excelRow, row, null);
            }

            // Totals row
            Row totalsRow = sheet.createRow(r);
            writeRow(totalsRow, report.getTotals(), boldStyle);

            for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ageing Excel", e);
        }
    }

    private void writeRow(Row excelRow, AgeingRowDto row, CellStyle style) {
        Cell c0 = excelRow.createCell(0); c0.setCellValue(row.getCode() != null ? row.getCode() : "");
        Cell c1 = excelRow.createCell(1); c1.setCellValue(row.getPartyName() != null ? row.getPartyName() : "");
        Cell c2 = excelRow.createCell(2); c2.setCellValue(dbl(row.getCurrent()));
        Cell c3 = excelRow.createCell(3); c3.setCellValue(dbl(row.getDays31_60()));
        Cell c4 = excelRow.createCell(4); c4.setCellValue(dbl(row.getDays61_90()));
        Cell c5 = excelRow.createCell(5); c5.setCellValue(dbl(row.getDays90Plus()));
        Cell c6 = excelRow.createCell(6); c6.setCellValue(dbl(row.getTotal()));
        if (style != null) {
            for (Cell c : new Cell[]{c0, c1, c2, c3, c4, c5, c6}) c.setCellStyle(style);
        }
    }

    private double dbl(BigDecimal v) {
        return v != null ? v.doubleValue() : 0.0;
    }
}
