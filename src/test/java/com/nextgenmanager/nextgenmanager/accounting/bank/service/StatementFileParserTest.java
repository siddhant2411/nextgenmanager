package com.nextgenmanager.nextgenmanager.accounting.bank.service;

import com.nextgenmanager.nextgenmanager.accounting.bank.model.BankStatementMapping;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementFileParserTest {

    /** HDFC-style export: account details above the table, quoted narrations with commas, a footer. */
    private static final String HDFC_CSV = """
            Account No,50100012345678
            Statement From,01/04/2026 To 30/04/2026
            Date,Narration,Chq./Ref.No.,Withdrawal Amt.,Deposit Amt.,Closing Balance
            02/04/2026,"NEFT-ACME TOOLS, PUNE",N123456789,,"1,18,000.00","2,18,000.00"
            05/04/2026,CHQ PAID-000123-SHARMA STEEL,000123,"25,000.00",,"1,93,000.00"
            05/04/2026,BANK CHARGES,,59.00,,"1,92,941.00"

            Total,,,"25,059.00","1,18,000.00",
            """;

    private static BankStatementMapping hdfc() {
        BankStatementMapping m = new BankStatementMapping();
        m.setHeaderRow(3);
        m.setDateColumn("Date");
        m.setDateFormat("dd/MM/yyyy");
        m.setNarrationColumn("narration");                // header match ignores case
        m.setReferenceColumn("Chq./Ref.No.");
        m.setWithdrawalColumn("Withdrawal Amt.");
        m.setDepositColumn("Deposit  Amt.");               // and repeated spaces
        m.setBalanceColumn("Closing Balance");
        return m;
    }

    @Test
    void csv_withHeaderBelowAccountDetails_parsesRowsAndSkipsTheFooter() {
        List<List<String>> rows = StatementFileParser.readRows(HDFC_CSV.getBytes(StandardCharsets.UTF_8), "april.csv");
        StatementFileParser.ParseResult r = StatementFileParser.parse(rows, hdfc());

        assertThat(r.rows()).hasSize(3);
        StatementFileParser.ParsedRow first = r.rows().get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 4, 2));
        assertThat(first.narration()).isEqualTo("NEFT-ACME TOOLS, PUNE");
        assertThat(first.deposit()).isEqualByComparingTo("118000");
        assertThat(first.withdrawal()).isEqualByComparingTo("0");
        assertThat(first.balance()).isEqualByComparingTo("218000");
        assertThat(r.rows().get(1).withdrawal()).isEqualByComparingTo("25000");
        assertThat(r.rows().get(2).reference()).isNull();
        assertThat(r.skipped()).singleElement().asString().contains("'Total'");
    }

    @Test
    void singleSignedColumn_withCrDrSuffixes_andSemicolons() {
        String csv = "Txn Date;Description;Amount;Balance\n"
                + "15-Apr-2026;UPI/RAVI;1,500.00 Cr;10,000.00 Cr\n"
                + "16-Apr-2026;ATM;(2,000.00);8,000.00\n"
                + "17-Apr-2026 10:42:11;SALARY;-45000;\n";
        BankStatementMapping m = new BankStatementMapping();
        m.setDateColumn("Txn Date");
        m.setNarrationColumn("Description");
        m.setAmountColumn("Amount");
        m.setBalanceColumn("Balance");

        StatementFileParser.ParseResult r = StatementFileParser.parse(
                StatementFileParser.readRows(csv.getBytes(StandardCharsets.UTF_8), "x.csv"), m);

        assertThat(r.rows()).hasSize(3);
        assertThat(r.rows().get(0).deposit()).isEqualByComparingTo("1500");
        assertThat(r.rows().get(1).withdrawal()).isEqualByComparingTo("2000");
        assertThat(r.rows().get(2).date()).isEqualTo(LocalDate.of(2026, 4, 17));   // time part dropped
        assertThat(r.rows().get(2).withdrawal()).isEqualByComparingTo("45000");
    }

    @Test
    void excel_dateCellsAndNumbers() throws Exception {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet();
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("Value Date");
            h.createCell(1).setCellValue("Debit");
            h.createCell(2).setCellValue("Credit");
            Row d = s.createRow(1);
            var cell = d.createCell(0);
            cell.setCellValue(LocalDate.of(2026, 5, 9));
            var style = wb.createCellStyle();
            style.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
            cell.setCellStyle(style);
            d.createCell(1).setCellValue(1234.5);
            wb.write(out);
            bytes = out.toByteArray();
        }
        BankStatementMapping m = new BankStatementMapping();
        m.setDateColumn("Value Date");
        m.setWithdrawalColumn("Debit");
        m.setDepositColumn("Credit");

        StatementFileParser.ParseResult r = StatementFileParser.parse(StatementFileParser.readRows(bytes, "s.xlsx"), m);

        assertThat(r.rows()).singleElement().satisfies(row -> {
            assertThat(row.date()).isEqualTo(LocalDate.of(2026, 5, 9));
            assertThat(row.withdrawal()).isEqualByComparingTo("1234.50");
        });
    }

    @Test
    void unknownColumn_namesTheColumnsThatExist() {
        BankStatementMapping m = hdfc();
        m.setDepositColumn("Credit");
        List<List<String>> rows = StatementFileParser.readRows(HDFC_CSV.getBytes(StandardCharsets.UTF_8), "a.csv");

        assertThatThrownBy(() -> StatementFileParser.parse(rows, m))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'Credit'")
                .hasMessageContaining("Deposit Amt.");
    }

    @Test
    void amounts() {
        assertThat(StatementFileParser.parseAmount("₹ 1,23,456.78")).isEqualByComparingTo(new BigDecimal("123456.78"));
        assertThat(StatementFileParser.parseAmount("75 Dr")).isEqualByComparingTo("-75");
        assertThat(StatementFileParser.parseAmount("")).isEqualByComparingTo("0");
        assertThatThrownBy(() -> StatementFileParser.parseAmount("abc")).isInstanceOf(NumberFormatException.class);
    }
}
