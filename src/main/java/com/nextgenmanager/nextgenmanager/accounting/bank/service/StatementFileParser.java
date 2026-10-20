package com.nextgenmanager.nextgenmanager.accounting.bank.service;

import com.nextgenmanager.nextgenmanager.accounting.bank.model.BankStatementMapping;
import org.apache.poi.ss.usermodel.*;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Reads a bank statement export — CSV (comma, semicolon or tab) or Excel (.xlsx/.xls) — into rows of
 * text, then turns rows into dated amounts using a {@link BankStatementMapping}. No Spring, no
 * database: everything here is a pure function of the file and the mapping.
 */
public final class StatementFileParser {

    private StatementFileParser() {}

    public record ParsedRow(int rowNumber, LocalDate date, String narration, String reference,
                            BigDecimal withdrawal, BigDecimal deposit, BigDecimal balance) {}

    public record ParseResult(List<ParsedRow> rows, List<String> skipped) {}

    /** Day-first formats used by Indian banks, tried in order when the mapping names none. */
    private static final List<DateTimeFormatter> COMMON_DATES = List.of(
            "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "d/M/yyyy", "d-M-yyyy",
            "dd/MM/yy", "dd-MM-yy", "dd-MMM-yyyy", "dd MMM yyyy", "dd-MMM-yy", "dd MMM yy", "d MMM yyyy"
    ).stream().map(StatementFileParser::formatter).toList();

    // ─── File → rows of text ─────────────────────────────────────────────────

    public static List<List<String>> readRows(byte[] bytes, String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) return readWorkbook(bytes);
        if (name.endsWith(".csv") || name.endsWith(".txt") || name.isEmpty()) return readCsv(bytes);
        throw new IllegalArgumentException("Upload the statement as CSV or Excel (.csv, .xlsx, .xls).");
    }

    private static List<List<String>> readWorkbook(byte[] bytes) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(Locale.ENGLISH);
            List<List<String>> rows = new ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                        Cell cell = row.getCell(c);
                        if (cell == null) { cells.add(""); continue; }
                        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                            cells.add(cell.getLocalDateTimeCellValue().toLocalDate().toString());   // ISO — always parses
                        } else {
                            cells.add(fmt.formatCellValue(cell).trim());
                        }
                    }
                }
                rows.add(cells);
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the Excel file: " + e.getMessage());
        }
    }

    static List<List<String>> readCsv(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        char delimiter = detectDelimiter(text);

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString().trim());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().trim());
            rows.add(row);
        }
        return rows;
    }

    /** The delimiter that appears most often (outside quotes) across the first few lines. */
    private static char detectDelimiter(String text) {
        char[] candidates = {',', ';', '\t'};
        int[] counts = new int[candidates.length];
        boolean quoted = false;
        int lines = 0;
        for (int i = 0; i < text.length() && lines < 20; i++) {
            char ch = text.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (!quoted && ch == '\n') lines++;
            else if (!quoted) for (int k = 0; k < candidates.length; k++) if (ch == candidates[k]) counts[k]++;
        }
        int best = 0;
        for (int k = 1; k < candidates.length; k++) if (counts[k] > counts[best]) best = k;
        return candidates[best];
    }

    // ─── Rows → dated amounts ────────────────────────────────────────────────

    public static List<String> headers(List<List<String>> rows, int headerRow) {
        if (headerRow < 1 || headerRow > rows.size()) {
            throw new IllegalArgumentException("Header row " + headerRow + " is outside the file (it has " + rows.size() + " rows).");
        }
        return rows.get(headerRow - 1);
    }

    public static ParseResult parse(List<List<String>> rows, BankStatementMapping m) {
        List<String> header = headers(rows, m.getHeaderRow());
        int date = column(header, m.getDateColumn(), true);
        int narration = column(header, m.getNarrationColumn(), false);
        int reference = column(header, m.getReferenceColumn(), false);
        int withdrawal = column(header, m.getWithdrawalColumn(), false);
        int deposit = column(header, m.getDepositColumn(), false);
        int amount = column(header, m.getAmountColumn(), false);
        int balance = column(header, m.getBalanceColumn(), false);
        boolean split = withdrawal >= 0 || deposit >= 0;
        if (!split && amount < 0) {
            throw new IllegalArgumentException("Map either a withdrawal and/or deposit column, or a single amount column.");
        }

        List<ParsedRow> out = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (int r = m.getHeaderRow(); r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int rowNo = r + 1;
            if (row.stream().allMatch(String::isBlank)) continue;
            String rawDate = cell(row, date);
            LocalDate d = parseDate(rawDate, m.getDateFormat());
            if (d == null) {
                skipped.add("Row " + rowNo + ": date '" + rawDate + "' not recognised");
                continue;
            }
            BigDecimal w;
            BigDecimal dep;
            try {
                if (split) {
                    w = parseAmount(cell(row, withdrawal)).abs();
                    dep = parseAmount(cell(row, deposit)).abs();
                } else {
                    BigDecimal signed = parseAmount(cell(row, amount));
                    w = signed.signum() < 0 ? signed.negate() : BigDecimal.ZERO;
                    dep = signed.signum() > 0 ? signed : BigDecimal.ZERO;
                }
            } catch (NumberFormatException e) {
                skipped.add("Row " + rowNo + ": amount not recognised (" + e.getMessage() + ")");
                continue;
            }
            if (w.signum() == 0 && dep.signum() == 0) {
                skipped.add("Row " + rowNo + ": no amount");
                continue;
            }
            if (w.signum() > 0 && dep.signum() > 0) {
                skipped.add("Row " + rowNo + ": both withdrawal and deposit filled");
                continue;
            }
            BigDecimal bal = null;
            if (balance >= 0 && !cell(row, balance).isBlank()) {
                try { bal = parseAmount(cell(row, balance)); } catch (NumberFormatException ignored) { /* balance is optional */ }
            }
            out.add(new ParsedRow(rowNo, d, blankToNull(cell(row, narration)), blankToNull(cell(row, reference)), w, dep, bal));
        }
        return new ParseResult(out, skipped);
    }

    private static int column(List<String> header, String name, boolean required) {
        if (name == null || name.isBlank()) {
            if (required) throw new IllegalArgumentException("The date column must be mapped.");
            return -1;
        }
        String want = normalise(name);
        for (int i = 0; i < header.size(); i++) if (normalise(header.get(i)).equals(want)) return i;
        throw new IllegalArgumentException("Column '" + name + "' is not in the header row. Columns found: "
                + String.join(" | ", header.stream().filter(h -> !h.isBlank()).toList()));
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String cell(List<String> row, int i) {
        return i >= 0 && i < row.size() && row.get(i) != null ? row.get(i).trim() : "";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    static LocalDate parseDate(String raw, String format) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        List<String> attempts = new ArrayList<>(List.of(s));
        int colon = s.indexOf(':');                                   // "15/04/2026 10:30:00" or "15 Apr 2026 10:30"
        int timeStart = colon > 0 ? s.lastIndexOf(' ', colon) : -1;
        if (timeStart > 0) attempts.add(s.substring(0, timeStart).trim());
        List<DateTimeFormatter> formats = format != null && !format.isBlank()
                ? List.of(formatter(format.trim()), DateTimeFormatter.ISO_LOCAL_DATE)
                : COMMON_DATES;
        for (String a : attempts) {
            for (DateTimeFormatter f : formats) {
                try { return LocalDate.parse(a, f); } catch (DateTimeParseException ignored) { /* next */ }
            }
        }
        return null;
    }

    /** "1,23,456.78", "₹ 500", "(250.00)", "-250", "1,000.00 Cr" (Cr = money in), "75 Dr". Blank is zero. */
    static BigDecimal parseAmount(String raw) {
        if (raw == null) return BigDecimal.ZERO;
        String s = raw.trim().toUpperCase(Locale.ROOT)
                .replace("₹", "").replace("INR", "").replace("RS.", "").replace(",", "").replace(" ", "");
        if (s.isEmpty() || s.equals("-")) return BigDecimal.ZERO;
        boolean negative = false;
        if (s.endsWith("CR")) s = s.substring(0, s.length() - 2);
        else if (s.endsWith("DR")) { s = s.substring(0, s.length() - 2); negative = true; }
        if (s.startsWith("(") && s.endsWith(")")) { s = s.substring(1, s.length() - 1); negative = !negative; }
        if (s.isEmpty()) return BigDecimal.ZERO;
        BigDecimal v = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        return negative ? v.negate() : v;
    }

    private static DateTimeFormatter formatter(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH);
    }
}
