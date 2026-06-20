package com.nextgenmanager.nextgenmanager.accounting.gst.hsn.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.GstSupport;
import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto.HsnSummaryDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto.HsnSummaryRow;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.sales.model.*;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesCreditNoteRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.TaxInvoiceRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HsnSummaryServiceImpl implements HsnSummaryService {

    private static final String UNCLASSIFIED = "UNCLASSIFIED";

    private final TaxInvoiceRepository taxInvoiceRepo;
    private final SalesCreditNoteRepository creditNoteRepo;
    private final GstSupport gst;

    // ── Mutable accumulator per (HSN, UQC, rate%) bucket ─────────────────────────

    private static final class Bucket {
        final String hsn, description, uqc;
        final int rate;
        BigDecimal qty = BigDecimal.ZERO, taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO, sgst = BigDecimal.ZERO, igst = BigDecimal.ZERO, cess = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        Bucket(String hsn, String description, String uqc, int rate) {
            this.hsn = hsn; this.description = description; this.uqc = uqc; this.rate = rate;
        }
    }

    @Override
    public HsnSummaryDto summary(LocalDate from, LocalDate to) {
        Map<String, Bucket> buckets = new LinkedHashMap<>();

        // Outward invoices (positive)
        for (TaxInvoice inv : taxInvoiceRepo.findForGstRegister(from, to)) {
            // InvoiceItem.totalAmount = pre-discount gross + taxes, so totalAmount − taxes = gross (wrong when
            // a header-level discount is present). Use invoice-header totals to build a proportional factor
            // so that each item's taxable = itemTaxes × (invTaxable / invTax). This distributes the correct
            // post-discount taxable across items in proportion to their tax amounts.
            BigDecimal invTaxable = nz(inv.getTaxableValue());
            BigDecimal invTax = nz(inv.getCgstAmount()).add(nz(inv.getSgstAmount())).add(nz(inv.getIgstAmount()));

            for (InvoiceItem it : inv.getItems()) {
                BigDecimal cgst = nz(it.getCgstAmount());
                BigDecimal sgst = nz(it.getSgstAmount());
                BigDecimal igst = nz(it.getIgstAmount());
                BigDecimal taxes = cgst.add(sgst).add(igst);
                // Derive per-item taxable proportionally from the invoice header (handles header discounts).
                BigDecimal taxable = invTax.signum() == 0
                        ? nz(it.getTotalAmount())   // fallback: zero-tax item (exempt supply)
                        : taxes.multiply(invTaxable).divide(invTax, 2, RoundingMode.HALF_UP);
                int rate = gst.effectiveRate(taxable, taxes).intValue();
                accumulate(buckets, it.getInventoryItem(), rate,
                        nz(it.getQty()), taxable, cgst, sgst, igst, BigDecimal.ZERO, taxable.add(taxes));
            }
        }

        // Credit notes (net, subtracted)
        for (SalesCreditNote cn : creditNoteRepo
                .findByStatusAndCreditNoteDateBetweenAndDeletedDateIsNullOrderByCreditNoteDateAscCreditNoteNumberAsc(
                        SalesCreditNoteStatus.CONFIRMED, from, to)) {
            boolean inter = gst.isInterState(cn.getCustomer());
            for (SalesCreditNoteItem it : cn.getItems()) {
                BigDecimal taxable = money(it.getReturnedQty() * it.getRate());
                BigDecimal[] split = gst.splitGst(money(it.getGstAmount()), inter);
                int rate = (int) Math.round(it.getGstRate());
                accumulate(buckets, it.getInventoryItem(), rate,
                        money(-it.getReturnedQty()), taxable.negate(),
                        split[0].negate(), split[1].negate(), split[2].negate(),
                        BigDecimal.ZERO, money(-it.getTotalAmount()));
            }
        }

        List<HsnSummaryRow> rows = new ArrayList<>();
        Bucket grand = new Bucket("TOTAL", null, null, 0);
        for (Bucket b : buckets.values()) {
            rows.add(new HsnSummaryRow(b.hsn, b.description, b.uqc,
                    b.qty, BigDecimal.valueOf(b.rate), b.taxable, b.cgst, b.sgst, b.igst, b.cess, b.total));
            grand.qty = grand.qty.add(b.qty);
            grand.taxable = grand.taxable.add(b.taxable);
            grand.cgst = grand.cgst.add(b.cgst);
            grand.sgst = grand.sgst.add(b.sgst);
            grand.igst = grand.igst.add(b.igst);
            grand.cess = grand.cess.add(b.cess);
            grand.total = grand.total.add(b.total);
        }
        HsnSummaryRow totals = new HsnSummaryRow("TOTAL", null, null,
                grand.qty, null, grand.taxable, grand.cgst, grand.sgst, grand.igst, grand.cess, grand.total);

        return new HsnSummaryDto(from, to, rows, totals);
    }

    private void accumulate(Map<String, Bucket> buckets, InventoryItem item, int rate,
                            BigDecimal qty, BigDecimal taxable,
                            BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal cess, BigDecimal total) {
        String hsn = item != null && item.getHsnCode() != null && !item.getHsnCode().isBlank()
                ? item.getHsnCode() : UNCLASSIFIED;
        String desc = item != null ? item.getName() : null;
        String uqc = item != null && item.getUom() != null ? item.getUom().name() : null;
        String key = hsn + "|" + uqc + "|" + rate;
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(hsn, desc, uqc, rate));
        b.qty = b.qty.add(qty);
        b.taxable = b.taxable.add(taxable);
        b.cgst = b.cgst.add(cgst);
        b.sgst = b.sgst.add(sgst);
        b.igst = b.igst.add(igst);
        b.cess = b.cess.add(cess);
        b.total = b.total.add(total);
    }

    // ── Excel ──────────────────────────────────────────────────────────────────

    private static final String[] COLS = {
            "HSN/SAC", "Description", "UQC", "Total Qty", "Rate %",
            "Taxable Value", "CGST", "SGST", "IGST", "Cess", "Total Value"};

    @Override
    public byte[] toExcel(HsnSummaryDto summary) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("HSN Summary");
            CellStyle bold = boldStyle(wb);

            int r = 0;
            sheet.createRow(r++).createCell(0)
                    .setCellValue("HSN/SAC Summary  " + summary.from() + " to " + summary.to());
            r++;
            Row header = sheet.createRow(r++);
            for (int i = 0; i < COLS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(COLS[i]);
                c.setCellStyle(bold);
            }
            for (HsnSummaryRow row : summary.rows()) writeRow(sheet.createRow(r++), row, null);
            writeRow(sheet.createRow(r), summary.totals(), bold);

            for (int i = 0; i < COLS.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HSN summary Excel", e);
        }
    }

    private void writeRow(Row row, HsnSummaryRow data, CellStyle style) {
        int c = 0;
        Cell[] cells = new Cell[COLS.length];
        cells[c] = row.createCell(c); cells[c++].setCellValue(s(data.hsnCode()));
        cells[c] = row.createCell(c); cells[c++].setCellValue(s(data.description()));
        cells[c] = row.createCell(c); cells[c++].setCellValue(s(data.uqc()));
        cells[c] = row.createCell(c); cells[c++].setCellValue(dbl(data.quantity()));
        cells[c] = row.createCell(c); cells[c++].setCellValue(dbl(data.rate()));
        cells[c] = row.createCell(c); cells[c++].setCellValue(dbl(data.taxableValue()));
        cells[c] = row.createCell(c); cells[c++].setCellValue(dbl(data.cgst()));
        cells[c] = row.createCell(c); cells[c++].setCellValue(dbl(data.sgst()));
        cells[c] = row.createCell(c); cells[c++].setCellValue(dbl(data.igst()));
        cells[c] = row.createCell(c); cells[c++].setCellValue(dbl(data.cess()));
        cells[c] = row.createCell(c); cells[c].setCellValue(dbl(data.totalValue()));
        if (style != null) for (Cell cell : cells) if (cell != null) cell.setCellStyle(style);
    }

    private CellStyle boldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    // ── Value helpers ──────────────────────────────────────────────────────────

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private static BigDecimal money(double v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP); }
    private static double dbl(BigDecimal v) { return v != null ? v.doubleValue() : 0.0; }
    private static String s(String v) { return v != null ? v : ""; }
}
