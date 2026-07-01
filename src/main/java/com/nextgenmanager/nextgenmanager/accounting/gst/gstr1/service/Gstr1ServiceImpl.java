package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.service;

import com.nextgenmanager.nextgenmanager.accounting.gst.GstSupport;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto.*;
import com.nextgenmanager.nextgenmanager.common.gst.GstState;
import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto.HsnSummaryDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.service.HsnSummaryService;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
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
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class Gstr1ServiceImpl implements Gstr1Service {

    /** B2C-Large threshold: inter-state unregistered invoices above this go to B2CL (locked: ₹2.5L). */
    private static final BigDecimal B2CL_THRESHOLD = new BigDecimal("250000");

    private final TaxInvoiceRepository taxInvoiceRepo;
    private final SalesCreditNoteRepository creditNoteRepo;
    private final HsnSummaryService hsnSummaryService;
    private final Gstr1JsonWriter jsonWriter;
    private final GstSupport gst;

    // ── Build ──────────────────────────────────────────────────────────────────

    @Override
    public Gstr1Dto build(LocalDate from, LocalDate to) {
        List<B2bInvoice> b2b = new ArrayList<>();
        List<B2clInvoice> b2cl = new ArrayList<>();
        Map<String, RateAcc> b2csByKey = new LinkedHashMap<>();   // key = pos|rate
        List<String> invoiceNos = new ArrayList<>();

        for (TaxInvoice inv : taxInvoiceRepo.findForGstRegister(from, to)) {
            invoiceNos.add(inv.getInvoiceNumber());
            Contact customer = inv.getSalesOrder() != null ? inv.getSalesOrder().getCustomer() : null;
            String pos = gst.placeOfSupply(customer);
            List<RateLine> lines = invoiceRateLines(inv);
            BigDecimal value = nz(inv.getTotalPayableAmount());

            if (gst.isB2B(customer)) {
                b2b.add(new B2bInvoice(customer.getGstNumber(), gst.partyName(customer),
                        inv.getInvoiceNumber(), inv.getInvoiceDate(), value, pos, false, lines));
            } else if (gst.isInterState(customer) && value.compareTo(B2CL_THRESHOLD) > 0) {
                b2cl.add(new B2clInvoice(inv.getInvoiceNumber(), inv.getInvoiceDate(), value, pos,
                        gst.partyName(customer), lines));
            } else {
                for (RateLine l : lines) {
                    String key = pos + "|" + l.rate().toPlainString();
                    b2csByKey.computeIfAbsent(key, k -> new RateAcc(pos, l.rate())).add(l);
                }
            }
        }

        List<B2csRow> b2cs = new ArrayList<>();
        for (RateAcc a : b2csByKey.values()) {
            b2cs.add(new B2csRow("OE", a.pos, a.rate, a.taxable, a.cgst, a.sgst, a.igst, a.cess));
        }

        // Credit notes → CDNR (registered) / CDNUR (unregistered)
        List<CdnRow> cdnr = new ArrayList<>();
        List<CdnRow> cdnur = new ArrayList<>();
        List<String> noteNos = new ArrayList<>();
        for (SalesCreditNote cn : creditNoteRepo
                .findByStatusAndCreditNoteDateBetweenAndDeletedDateIsNullOrderByCreditNoteDateAscCreditNoteNumberAsc(
                        SalesCreditNoteStatus.CONFIRMED, from, to)) {
            noteNos.add(cn.getCreditNoteNumber());
            Contact customer = cn.getCustomer();
            String pos = gst.placeOfSupply(customer);
            List<RateLine> lines = creditNoteRateLines(cn);
            CdnRow row = new CdnRow(gst.isB2B(customer) ? customer.getGstNumber() : null,
                    gst.partyName(customer), cn.getCreditNoteNumber(), cn.getCreditNoteDate(),
                    "C", money(cn.getTotalAmount()), pos, lines);
            if (gst.isB2B(customer)) cdnr.add(row); else cdnur.add(row);
        }

        HsnSummaryDto hsn = hsnSummaryService.summary(from, to);

        List<DocSeries> docs = new ArrayList<>();
        if (!invoiceNos.isEmpty()) docs.add(series("Tax Invoices", invoiceNos));
        if (!noteNos.isEmpty()) docs.add(series("Credit Notes", noteNos));

        return new Gstr1Dto(gst.companyGstin(), from, to, b2b, b2cl, b2cs, cdnr, cdnur, hsn, docs,
                totals(b2b, b2cl, b2cs, cdnr, cdnur));
    }

    // ── Rate-line derivation ─────────────────────────────────────────────────────

    /**
     * Produces rate-wise lines from the invoice header amounts.
     * InvoiceItem.totalAmount = pre-discount gross + taxes (not post-discount taxable + taxes),
     * so item-level subtraction gives the wrong taxable base when a header discount is present.
     * The header taxableValue already reflects the discount and ties to the GL voucher.
     * For the rare multi-rate invoice this collapses to a single blended line; that is acceptable
     * for GSTR-1 purposes in this release.
     */
    private List<RateLine> invoiceRateLines(TaxInvoice inv) {
        BigDecimal taxable = nz(inv.getTaxableValue());
        BigDecimal cgst = nz(inv.getCgstAmount());
        BigDecimal sgst = nz(inv.getSgstAmount());
        BigDecimal igst = nz(inv.getIgstAmount());
        BigDecimal taxes = cgst.add(sgst).add(igst);
        int rate = gst.effectiveRate(taxable, taxes).intValue();
        return List.of(new RateLine(BigDecimal.valueOf(rate), taxable, cgst, sgst, igst, BigDecimal.ZERO));
    }

    /** Groups a credit note's line items into rate-wise lines; combined GST split by treatment. */
    private List<RateLine> creditNoteRateLines(SalesCreditNote cn) {
        boolean inter = gst.isInterState(cn.getCustomer());
        Map<Integer, RateAcc> byRate = new LinkedHashMap<>();
        for (SalesCreditNoteItem it : cn.getItems()) {
            BigDecimal taxable = money(it.getReturnedQty() * it.getRate());
            BigDecimal[] split = gst.splitGst(money(it.getGstAmount()), inter);
            int rate = (int) Math.round(it.getGstRate());
            byRate.computeIfAbsent(rate, k -> new RateAcc(null, BigDecimal.valueOf(rate)))
                    .add(taxable, split[0], split[1], split[2], BigDecimal.ZERO);
        }
        return byRate.values().stream().map(RateAcc::toLine).toList();
    }

    private DocSeries series(String nature, List<String> numbers) {
        List<String> sorted = new ArrayList<>(numbers);
        Collections.sort(sorted);
        return new DocSeries(nature, sorted.get(0), sorted.get(sorted.size() - 1), sorted.size(), 0);
    }

    // ── Totals (net of credit notes; ties to GSTR-3B 3.1) ────────────────────────

    private Gstr1Dto.Gstr1Totals totals(List<B2bInvoice> b2b, List<B2clInvoice> b2cl, List<B2csRow> b2cs,
                                        List<CdnRow> cdnr, List<CdnRow> cdnur) {
        RateAcc t = new RateAcc(null, null);
        int docs = 0;
        for (B2bInvoice i : b2b) { for (RateLine l : i.items()) t.add(l); docs++; }
        for (B2clInvoice i : b2cl) { for (RateLine l : i.items()) t.add(l); docs++; }
        for (B2csRow r : b2cs) t.add(r.taxableValue(), r.cgst(), r.sgst(), r.igst(), r.cess());
        for (CdnRow n : cdnr) { for (RateLine l : n.items()) t.subtract(l); docs++; }
        for (CdnRow n : cdnur) { for (RateLine l : n.items()) t.subtract(l); docs++; }
        return new Gstr1Dto.Gstr1Totals(t.taxable, t.cgst, t.sgst, t.igst, t.cess, docs);
    }

    // ── JSON delegate ────────────────────────────────────────────────────────────

    @Override
    public byte[] toJson(Gstr1Dto gstr1) {
        return jsonWriter.write(gstr1);
    }

    // ── Excel: one sheet per table ───────────────────────────────────────────────

    @Override
    public byte[] toExcel(Gstr1Dto g) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = boldStyle(wb);
            b2bSheet(wb, bold, g);
            b2clSheet(wb, bold, g);
            b2csSheet(wb, bold, g);
            cdnSheet(wb, bold, "CDNR", g.cdnr());
            cdnSheet(wb, bold, "CDNUR", g.cdnur());
            hsnSheet(wb, bold, g);
            docsSheet(wb, bold, g);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate GSTR-1 Excel", e);
        }
    }

    private void b2bSheet(Workbook wb, CellStyle bold, Gstr1Dto g) {
        Sheet sh = wb.createSheet("B2B");
        int r = headerRow(sh, bold, "GSTIN", "Party", "Invoice No", "Date", "Invoice Value",
                "POS", "Rate %", "Taxable Value", "CGST", "SGST", "IGST", "Cess");
        for (B2bInvoice inv : g.b2b()) {
            for (RateLine l : inv.items()) {
                Row row = sh.createRow(r++);
                int c = 0;
                cell(row, c++, inv.ctin());
                cell(row, c++, inv.partyName());
                cell(row, c++, inv.invoiceNo());
                cell(row, c++, date(inv.invoiceDate()));
                num(row, c++, inv.invoiceValue());
                cell(row, c++, GstState.labelFor(inv.placeOfSupply()));
                num(row, c++, l.rate());
                num(row, c++, l.taxableValue());
                num(row, c++, l.cgst());
                num(row, c++, l.sgst());
                num(row, c++, l.igst());
                num(row, c, l.cess());
            }
        }
        autoSize(sh, 12);
    }

    private void b2clSheet(Workbook wb, CellStyle bold, Gstr1Dto g) {
        Sheet sh = wb.createSheet("B2CL");
        int r = headerRow(sh, bold, "Invoice No", "Date", "Invoice Value", "POS", "Party",
                "Rate %", "Taxable Value", "CGST", "SGST", "IGST", "Cess");
        for (B2clInvoice inv : g.b2cl()) {
            for (RateLine l : inv.items()) {
                Row row = sh.createRow(r++);
                int c = 0;
                cell(row, c++, inv.invoiceNo());
                cell(row, c++, date(inv.invoiceDate()));
                num(row, c++, inv.invoiceValue());
                cell(row, c++, GstState.labelFor(inv.placeOfSupply()));
                cell(row, c++, inv.partyName());
                num(row, c++, l.rate());
                num(row, c++, l.taxableValue());
                num(row, c++, l.cgst());
                num(row, c++, l.sgst());
                num(row, c++, l.igst());
                num(row, c, l.cess());
            }
        }
        autoSize(sh, 11);
    }

    private void b2csSheet(Workbook wb, CellStyle bold, Gstr1Dto g) {
        Sheet sh = wb.createSheet("B2CS");
        int r = headerRow(sh, bold, "Type", "POS", "Rate %", "Taxable Value", "CGST", "SGST", "IGST", "Cess");
        for (B2csRow row0 : g.b2cs()) {
            Row row = sh.createRow(r++);
            int c = 0;
            cell(row, c++, row0.type());
            cell(row, c++, GstState.labelFor(row0.placeOfSupply()));
            num(row, c++, row0.rate());
            num(row, c++, row0.taxableValue());
            num(row, c++, row0.cgst());
            num(row, c++, row0.sgst());
            num(row, c++, row0.igst());
            num(row, c, row0.cess());
        }
        autoSize(sh, 8);
    }

    private void cdnSheet(Workbook wb, CellStyle bold, String name, List<CdnRow> notes) {
        Sheet sh = wb.createSheet(name);
        int r = headerRow(sh, bold, "GSTIN", "Party", "Note No", "Date", "Type", "Note Value",
                "POS", "Rate %", "Taxable Value", "CGST", "SGST", "IGST", "Cess");
        for (CdnRow n : notes) {
            for (RateLine l : n.items()) {
                Row row = sh.createRow(r++);
                int c = 0;
                cell(row, c++, n.ctin());
                cell(row, c++, n.partyName());
                cell(row, c++, n.noteNo());
                cell(row, c++, date(n.noteDate()));
                cell(row, c++, n.noteType());
                num(row, c++, n.noteValue());
                cell(row, c++, GstState.labelFor(n.placeOfSupply()));
                num(row, c++, l.rate());
                num(row, c++, l.taxableValue());
                num(row, c++, l.cgst());
                num(row, c++, l.sgst());
                num(row, c++, l.igst());
                num(row, c, l.cess());
            }
        }
        autoSize(sh, 13);
    }

    private void hsnSheet(Workbook wb, CellStyle bold, Gstr1Dto g) {
        Sheet sh = wb.createSheet("HSN");
        int r = headerRow(sh, bold, "HSN/SAC", "Description", "UQC", "Total Qty", "Rate %",
                "Taxable Value", "CGST", "SGST", "IGST", "Cess", "Total Value");
        g.hsn().rows().forEach(row0 -> {
            Row row = sh.createRow(sh.getLastRowNum() + 1);
            int c = 0;
            cell(row, c++, row0.hsnCode());
            cell(row, c++, row0.description());
            cell(row, c++, row0.uqc());
            num(row, c++, row0.quantity());
            num(row, c++, row0.rate());
            num(row, c++, row0.taxableValue());
            num(row, c++, row0.cgst());
            num(row, c++, row0.sgst());
            num(row, c++, row0.igst());
            num(row, c++, row0.cess());
            num(row, c, row0.totalValue());
        });
        autoSize(sh, 11);
    }

    private void docsSheet(Workbook wb, CellStyle bold, Gstr1Dto g) {
        Sheet sh = wb.createSheet("DOCS");
        int r = headerRow(sh, bold, "Nature of Document", "From", "To", "Total Issued", "Cancelled");
        for (DocSeries d : g.docs()) {
            Row row = sh.createRow(r++);
            int c = 0;
            cell(row, c++, d.nature());
            cell(row, c++, d.fromNo());
            cell(row, c++, d.toNo());
            row.createCell(c++).setCellValue(d.totalCount());
            row.createCell(c).setCellValue(d.cancelled());
        }
        autoSize(sh, 5);
    }

    // ── Excel helpers ────────────────────────────────────────────────────────────

    private int headerRow(Sheet sh, CellStyle bold, String... cols) {
        Row h = sh.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(bold);
        }
        return 1;
    }

    private CellStyle boldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void cell(Row row, int c, String v) { row.createCell(c).setCellValue(v != null ? v : ""); }
    private void num(Row row, int c, BigDecimal v) { row.createCell(c).setCellValue(v != null ? v.doubleValue() : 0.0); }
    private void autoSize(Sheet sh, int cols) { for (int i = 0; i < cols; i++) sh.autoSizeColumn(i); }
    private static String date(LocalDate d) { return d != null ? d.toString() : ""; }

    // ── Accumulator ──────────────────────────────────────────────────────────────

    private static final class RateAcc {
        final String pos;
        final BigDecimal rate;
        BigDecimal taxable = BigDecimal.ZERO, cgst = BigDecimal.ZERO, sgst = BigDecimal.ZERO,
                igst = BigDecimal.ZERO, cess = BigDecimal.ZERO;
        RateAcc(String pos, BigDecimal rate) { this.pos = pos; this.rate = rate; }

        void add(RateLine l) { add(l.taxableValue(), l.cgst(), l.sgst(), l.igst(), l.cess()); }
        void subtract(RateLine l) {
            taxable = taxable.subtract(nz(l.taxableValue()));
            cgst = cgst.subtract(nz(l.cgst()));
            sgst = sgst.subtract(nz(l.sgst()));
            igst = igst.subtract(nz(l.igst()));
            cess = cess.subtract(nz(l.cess()));
        }
        void add(BigDecimal t, BigDecimal c, BigDecimal s, BigDecimal i, BigDecimal cs) {
            taxable = taxable.add(nz(t));
            cgst = cgst.add(nz(c));
            sgst = sgst.add(nz(s));
            igst = igst.add(nz(i));
            cess = cess.add(nz(cs));
        }
        RateLine toLine() { return new RateLine(rate, taxable, cgst, sgst, igst, cess); }
    }

    // ── Value helpers ──────────────────────────────────────────────────────────

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private static BigDecimal money(double v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP); }
}
