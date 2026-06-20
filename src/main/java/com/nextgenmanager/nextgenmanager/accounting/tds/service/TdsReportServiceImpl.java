package com.nextgenmanager.nextgenmanager.accounting.tds.service;

import com.nextgenmanager.nextgenmanager.accounting.tds.dto.TdsEntryDto;
import com.nextgenmanager.nextgenmanager.accounting.tds.model.TdsEntry;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsChallanRepository;
import com.nextgenmanager.nextgenmanager.accounting.tds.repository.TdsEntryRepository;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TdsReportServiceImpl implements TdsReportService {

    private final TdsEntryRepository entryRepo;
    private final TdsChallanRepository challanRepo;
    private final CompanyDetailsRepository companyDetailsRepository;

    @Override
    public List<TdsEntryDto> register(String financialYear, String quarter) {
        return entryRepo.findForQuarter(financialYear, quarter).stream()
                .map(TdsMapper::toEntryDto)
                .toList();
    }

    @Override
    public byte[] export26Q(String financialYear, String quarter) {
        List<TdsEntry> entries = entryRepo.findForQuarter(financialYear, quarter);
        CompanyDetails company = companyDetailsRepository.findAll().stream().findFirst().orElse(null);

        // Challan number lookup for the deductee rows.
        Map<Long, String> challanNumbers = new HashMap<>();
        challanRepo.findByFinancialYearAndDeletedDateIsNullOrderByDepositDateDesc(financialYear)
                .forEach(c -> challanNumbers.put(c.getId(), c.getChallanNumber()));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("26Q " + quarter);

            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            CellStyle bold = wb.createCellStyle();
            bold.setFont(boldFont);

            int r = 0;
            r = metaRow(sheet, r, "Form 26Q - TDS on payments other than salary", bold);
            r = metaRow(sheet, r, "Deductor: " + (company != null ? safe(company.getCompanyName()) : ""), null);
            r = metaRow(sheet, r, "TAN: " + (company != null ? safe(company.getTan()) : "")
                    + "    PAN: " + (company != null ? safe(company.getPanNumber()) : ""), null);
            r = metaRow(sheet, r, "Financial Year: " + financialYear + "    Quarter: " + quarter, null);
            r++; // spacer

            String[] columns = {"Sr", "Deductee", "PAN", "Section", "Date of Payment",
                    "Amount Paid", "TDS", "Rate %", "Status", "Challan No"};
            Row header = sheet.createRow(r++);
            for (int i = 0; i < columns.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(columns[i]);
                c.setCellStyle(bold);
            }

            int sr = 1;
            BigDecimal totalPaid = BigDecimal.ZERO, totalTds = BigDecimal.ZERO;
            for (TdsEntry e : entries) {
                Row row = sheet.createRow(r++);
                TdsEntryDto d = TdsMapper.toEntryDto(e);
                row.createCell(0).setCellValue(sr++);
                row.createCell(1).setCellValue(safe(d.getDeducteeName()));
                row.createCell(2).setCellValue(safe(d.getDeducteePan()));
                row.createCell(3).setCellValue(safe(d.getSection()));
                row.createCell(4).setCellValue(d.getDeductionDate() != null ? d.getDeductionDate().toString() : "");
                row.createCell(5).setCellValue(dbl(d.getTaxableAmount()));
                row.createCell(6).setCellValue(dbl(d.getTdsAmount()));
                row.createCell(7).setCellValue(dbl(d.getRate()));
                row.createCell(8).setCellValue(safe(d.getStatus()));
                row.createCell(9).setCellValue(d.getChallanId() != null
                        ? challanNumbers.getOrDefault(d.getChallanId(), "") : "");
                totalPaid = totalPaid.add(nz(d.getTaxableAmount()));
                totalTds = totalTds.add(nz(d.getTdsAmount()));
            }

            Row totals = sheet.createRow(r);
            Cell tLabel = totals.createCell(4); tLabel.setCellValue("Total"); tLabel.setCellStyle(bold);
            Cell tPaid = totals.createCell(5); tPaid.setCellValue(dbl(totalPaid)); tPaid.setCellStyle(bold);
            Cell tTds = totals.createCell(6); tTds.setCellValue(dbl(totalTds)); tTds.setCellStyle(bold);

            for (int i = 0; i < columns.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate 26Q Excel", e);
        }
    }

    private int metaRow(Sheet sheet, int r, String text, CellStyle style) {
        Row row = sheet.createRow(r);
        Cell c = row.createCell(0);
        c.setCellValue(text);
        if (style != null) c.setCellStyle(style);
        return r + 1;
    }

    private static String safe(String v) { return v != null ? v : ""; }
    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private static double dbl(BigDecimal v) { return v != null ? v.doubleValue() : 0.0; }
}
