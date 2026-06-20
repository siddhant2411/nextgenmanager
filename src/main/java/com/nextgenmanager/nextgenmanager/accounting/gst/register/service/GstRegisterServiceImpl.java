package com.nextgenmanager.nextgenmanager.accounting.gst.register.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.GstSupport;
import com.nextgenmanager.nextgenmanager.common.gst.GstState;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.GstTotals;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.InwardRegisterDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.InwardRegisterRow;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.OutwardRegisterDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.OutwardRegisterRow;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.purchase.model.DebitNote;
import com.nextgenmanager.nextgenmanager.purchase.model.DebitNoteStatus;
import com.nextgenmanager.nextgenmanager.purchase.model.VendorInvoice;
import com.nextgenmanager.nextgenmanager.purchase.repository.DebitNoteRepository;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorInvoiceRepository;
import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNote;
import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNoteStatus;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GstRegisterServiceImpl implements GstRegisterService {

    private final TaxInvoiceRepository taxInvoiceRepo;
    private final SalesCreditNoteRepository creditNoteRepo;
    private final VendorInvoiceRepository vendorInvoiceRepo;
    private final DebitNoteRepository debitNoteRepo;
    private final GstSupport gst;

    // ── Outward register ───────────────────────────────────────────────────────

    @Override
    public OutwardRegisterDto outwardRegister(LocalDate from, LocalDate to) {
        List<OutwardRegisterRow> rows = new ArrayList<>();

        for (TaxInvoice inv : taxInvoiceRepo.findForGstRegister(from, to)) {
            Contact customer = inv.getSalesOrder() != null ? inv.getSalesOrder().getCustomer() : null;
            BigDecimal taxable = nz(inv.getTaxableValue());
            BigDecimal cgst = nz(inv.getCgstAmount());
            BigDecimal sgst = nz(inv.getSgstAmount());
            BigDecimal igst = nz(inv.getIgstAmount());
            BigDecimal cess = BigDecimal.ZERO;
            BigDecimal total = nz(inv.getTotalPayableAmount());
            rows.add(new OutwardRegisterRow("INV", inv.getInvoiceNumber(), inv.getInvoiceDate(),
                    customer != null ? customer.getGstNumber() : null,
                    gst.partyName(customer), gst.placeOfSupply(customer),
                    taxable, gst.effectiveRate(taxable, cgst.add(sgst).add(igst)),
                    cgst, sgst, igst, cess, total));
        }

        for (SalesCreditNote cn : creditNoteRepo
                .findByStatusAndCreditNoteDateBetweenAndDeletedDateIsNullOrderByCreditNoteDateAscCreditNoteNumberAsc(
                        SalesCreditNoteStatus.CONFIRMED, from, to)) {
            Contact customer = cn.getCustomer();
            BigDecimal taxable = money(cn.getSubtotal());
            BigDecimal[] split = gst.splitGst(money(cn.getTotalGstAmount()), gst.isInterState(customer));
            BigDecimal total = money(cn.getTotalAmount());
            rows.add(new OutwardRegisterRow("CRN", cn.getCreditNoteNumber(), cn.getCreditNoteDate(),
                    customer != null ? customer.getGstNumber() : null,
                    gst.partyName(customer), gst.placeOfSupply(customer),
                    taxable, gst.effectiveRate(taxable, split[0].add(split[1]).add(split[2])),
                    split[0], split[1], split[2], BigDecimal.ZERO, total));
        }

        return new OutwardRegisterDto(from, to, rows, outwardTotals(rows));
    }

    // ── Inward register ──────────────────────────────────────────────────────────

    @Override
    public InwardRegisterDto inwardRegister(LocalDate from, LocalDate to) {
        List<InwardRegisterRow> rows = new ArrayList<>();

        for (VendorInvoice vi : vendorInvoiceRepo.findForGstRegister(from, to)) {
            Contact vendor = vi.getVendor();
            BigDecimal cgst = nz(vi.getCgstAmount());
            BigDecimal sgst = nz(vi.getSgstAmount());
            BigDecimal igst = nz(vi.getIgstAmount());
            BigDecimal cess = nz(vi.getCessAmount());
            BigDecimal total = nz(vi.getGrandTotal());
            // Header subtotal is gross/pre-discount; real taxable = grandTotal − taxes (ties to GL).
            BigDecimal taxable = total.subtract(cgst).subtract(sgst).subtract(igst).subtract(cess);
            rows.add(new InwardRegisterRow("BILL", vi.getInvoiceNumber(), vi.getInvoiceDate(),
                    vendor != null ? vendor.getGstNumber() : null,
                    gst.partyName(vendor), gst.placeOfSupply(vendor),
                    taxable, gst.effectiveRate(taxable, cgst.add(sgst).add(igst)),
                    cgst, sgst, igst, cess, total,
                    vi.isItcEligible(), vi.isReverseCharge()));
        }

        for (DebitNote dn : debitNoteRepo
                .findByStatusAndDebitNoteDateBetweenAndDeletedDateIsNullOrderByDebitNoteDateAscDebitNoteNumberAsc(
                        DebitNoteStatus.CONFIRMED, from, to)) {
            Contact vendor = dn.getVendor();
            BigDecimal taxable = money(dn.getSubtotal());
            BigDecimal[] split = gst.splitGst(money(dn.getTotalGstAmount()), gst.isInterState(vendor));
            BigDecimal total = money(dn.getTotalAmount());
            rows.add(new InwardRegisterRow("DBN", dn.getDebitNoteNumber(), dn.getDebitNoteDate(),
                    vendor != null ? vendor.getGstNumber() : null,
                    gst.partyName(vendor), gst.placeOfSupply(vendor),
                    taxable, gst.effectiveRate(taxable, split[0].add(split[1]).add(split[2])),
                    split[0], split[1], split[2], BigDecimal.ZERO, total,
                    true, false));
        }

        return new InwardRegisterDto(from, to, rows, inwardTotals(rows));
    }

    // ── Totals ─────────────────────────────────────────────────────────────────

    private GstTotals outwardTotals(List<OutwardRegisterRow> rows) {
        GstTotals t = GstTotals.zero();
        for (OutwardRegisterRow r : rows) {
            t = t.add(r.taxableValue(), r.cgst(), r.sgst(), r.igst(), r.cess(), r.total());
        }
        return t;
    }

    private GstTotals inwardTotals(List<InwardRegisterRow> rows) {
        GstTotals t = GstTotals.zero();
        for (InwardRegisterRow r : rows) {
            t = t.add(r.taxableValue(), r.cgst(), r.sgst(), r.igst(), r.cess(), r.total());
        }
        return t;
    }

    // ── Excel ──────────────────────────────────────────────────────────────────

    private static final String[] OUTWARD_COLS = {
            "Type", "Doc No", "Doc Date", "GSTIN", "Party", "POS",
            "Taxable Value", "Rate %", "CGST", "SGST", "IGST", "Cess", "Total"};
    private static final String[] INWARD_COLS = {
            "Type", "Doc No", "Doc Date", "GSTIN", "Vendor", "POS",
            "Taxable Value", "Rate %", "CGST", "SGST", "IGST", "Cess", "Total", "ITC Eligible", "RCM"};

    @Override
    public byte[] outwardExcel(OutwardRegisterDto register) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Outward Register");
            CellStyle bold = boldStyle(wb);

            int r = 0;
            sheet.createRow(r++).createCell(0)
                    .setCellValue("Outward Supplies Register  " + register.from() + " to " + register.to());
            r++;
            header(sheet.createRow(r++), OUTWARD_COLS, bold);

            for (OutwardRegisterRow row : register.rows()) {
                Row er = sheet.createRow(r++);
                int c = 0;
                er.createCell(c++).setCellValue(s(row.docType()));
                er.createCell(c++).setCellValue(s(row.docNo()));
                er.createCell(c++).setCellValue(s(date(row.docDate())));
                er.createCell(c++).setCellValue(s(row.gstin()));
                er.createCell(c++).setCellValue(s(row.partyName()));
                er.createCell(c++).setCellValue(s(GstState.labelFor(row.placeOfSupply())));
                er.createCell(c++).setCellValue(dbl(row.taxableValue()));
                er.createCell(c++).setCellValue(dbl(row.rate()));
                er.createCell(c++).setCellValue(dbl(row.cgst()));
                er.createCell(c++).setCellValue(dbl(row.sgst()));
                er.createCell(c++).setCellValue(dbl(row.igst()));
                er.createCell(c++).setCellValue(dbl(row.cess()));
                er.createCell(c).setCellValue(dbl(row.total()));
            }
            totalsRow(sheet.createRow(r), register.totals(), bold, 6, OUTWARD_COLS.length);

            autoSize(sheet, OUTWARD_COLS.length);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate outward register Excel", e);
        }
    }

    @Override
    public byte[] inwardExcel(InwardRegisterDto register) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Inward Register");
            CellStyle bold = boldStyle(wb);

            int r = 0;
            sheet.createRow(r++).createCell(0)
                    .setCellValue("Inward Supplies / ITC Register  " + register.from() + " to " + register.to());
            r++;
            header(sheet.createRow(r++), INWARD_COLS, bold);

            for (InwardRegisterRow row : register.rows()) {
                Row er = sheet.createRow(r++);
                int c = 0;
                er.createCell(c++).setCellValue(s(row.docType()));
                er.createCell(c++).setCellValue(s(row.docNo()));
                er.createCell(c++).setCellValue(s(date(row.docDate())));
                er.createCell(c++).setCellValue(s(row.gstin()));
                er.createCell(c++).setCellValue(s(row.partyName()));
                er.createCell(c++).setCellValue(s(GstState.labelFor(row.placeOfSupply())));
                er.createCell(c++).setCellValue(dbl(row.taxableValue()));
                er.createCell(c++).setCellValue(dbl(row.rate()));
                er.createCell(c++).setCellValue(dbl(row.cgst()));
                er.createCell(c++).setCellValue(dbl(row.sgst()));
                er.createCell(c++).setCellValue(dbl(row.igst()));
                er.createCell(c++).setCellValue(dbl(row.cess()));
                er.createCell(c++).setCellValue(dbl(row.total()));
                er.createCell(c++).setCellValue(row.itcEligible() ? "Yes" : "No");
                er.createCell(c).setCellValue(row.reverseCharge() ? "Yes" : "No");
            }
            totalsRow(sheet.createRow(r), register.totals(), bold, 6, INWARD_COLS.length - 2);

            autoSize(sheet, INWARD_COLS.length);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate inward register Excel", e);
        }
    }

    // ── Excel helpers ────────────────────────────────────────────────────────────

    private CellStyle boldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void header(Row row, String[] cols, CellStyle bold) {
        for (int i = 0; i < cols.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(bold);
        }
    }

    /** Writes register totals into the numeric columns starting at {@code taxableCol}. */
    private void totalsRow(Row row, GstTotals t, CellStyle bold, int taxableCol, int lastCol) {
        Cell label = row.createCell(0);
        label.setCellValue("TOTAL");
        label.setCellStyle(bold);
        BigDecimal[] vals = {t.taxableValue(), null, t.cgst(), t.sgst(), t.igst(), t.cess(), t.total()};
        // columns: taxable, rate(skip), cgst, sgst, igst, cess, total — laid out from taxableCol
        int col = taxableCol;
        for (BigDecimal v : vals) {
            Cell c = row.createCell(col++);
            if (v != null) c.setCellValue(dbl(v));
            c.setCellStyle(bold);
        }
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) sheet.autoSizeColumn(i);
    }

    // ── Value helpers ──────────────────────────────────────────────────────────

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static double dbl(BigDecimal v) {
        return v != null ? v.doubleValue() : 0.0;
    }

    private static String s(String v) {
        return v != null ? v : "";
    }

    private static String date(LocalDate d) {
        return d != null ? d.toString() : "";
    }
}
