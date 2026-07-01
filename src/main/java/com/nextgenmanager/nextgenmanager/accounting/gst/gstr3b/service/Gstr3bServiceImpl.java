package com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgenmanager.nextgenmanager.accounting.gst.GstSupport;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr3b.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.InwardRegisterRow;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.OutwardRegisterDto;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.dto.OutwardRegisterRow;
import com.nextgenmanager.nextgenmanager.accounting.gst.register.service.GstRegisterService;
import com.nextgenmanager.nextgenmanager.accounting.posting.LedgerResolver;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.common.gst.GstState;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class Gstr3bServiceImpl implements Gstr3bService {

    private static final BigDecimal TIE_TOLERANCE = BigDecimal.ONE;

    private static final List<String> OUTPUT_CODES = List.of(
            LedgerResolver.OUTPUT_CGST, LedgerResolver.OUTPUT_SGST,
            LedgerResolver.OUTPUT_IGST, LedgerResolver.OUTPUT_CESS);
    private static final List<String> INPUT_CODES = List.of(
            LedgerResolver.INPUT_CGST, LedgerResolver.INPUT_SGST,
            LedgerResolver.INPUT_IGST, LedgerResolver.INPUT_CESS);

    private final GstRegisterService registerService;
    private final VoucherLineRepository voucherLineRepo;
    private final ObjectMapper objectMapper;
    private final GstSupport gst;

    // ── Build ──────────────────────────────────────────────────────────────────

    @Override
    public Gstr3bDto build(LocalDate from, LocalDate to) {
        OutwardRegisterDto outward = registerService.outwardRegister(from, to);

        // 3.1(a): invoices add, credit notes subtract
        TaxAmounts outwardTaxable = TaxAmounts.zero();
        for (OutwardRegisterRow r : outward.rows()) {
            outwardTaxable = "CRN".equals(r.docType())
                    ? outwardTaxable.subtract(r.taxableValue(), r.igst(), r.cgst(), r.sgst(), r.cess())
                    : outwardTaxable.add(r.taxableValue(), r.igst(), r.cgst(), r.sgst(), r.cess());
        }

        // 3.2: inter-state B2C invoices (no GSTIN, has IGST), grouped by place of supply
        Map<String, BigDecimal[]> sec32map = new LinkedHashMap<>();
        for (OutwardRegisterRow r : outward.rows()) {
            if ("INV".equals(r.docType()) && r.gstin() == null && r.igst() != null && r.igst().signum() > 0) {
                String pos = r.placeOfSupply() != null ? r.placeOfSupply() : "UNK";
                sec32map.compute(pos, (k, v) -> {
                    if (v == null) v = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
                    v[0] = v[0].add(r.taxableValue() != null ? r.taxableValue() : BigDecimal.ZERO);
                    v[1] = v[1].add(r.igst());
                    return v;
                });
            }
        }
        List<Section32Row> section32 = sec32map.entrySet().stream()
                .map(e -> new Section32Row(e.getKey(), GstState.nameFor(e.getKey()),
                        e.getValue()[0], e.getValue()[1]))
                .toList();

        // 4A: eligible ITC from bills (always positive)
        // 4B: ITC reversed via debit notes (always positive)
        // 4D: ineligible ITC from bills
        // Debit notes go to 4B regardless of itcEligible flag (they reverse previously claimed ITC).
        TaxAmounts inwardRcm    = TaxAmounts.zero();
        TaxAmounts itcAvailable = TaxAmounts.zero();  // 4A
        TaxAmounts itcReversed  = TaxAmounts.zero();  // 4B
        TaxAmounts itcIneligible = TaxAmounts.zero(); // 4D

        for (InwardRegisterRow r : registerService.inwardRegister(from, to).rows()) {
            if ("DBN".equals(r.docType())) {
                itcReversed = itcReversed.add(r.taxableValue(), r.igst(), r.cgst(), r.sgst(), r.cess());
            } else {
                if (r.reverseCharge()) {
                    inwardRcm = inwardRcm.add(r.taxableValue(), r.igst(), r.cgst(), r.sgst(), r.cess());
                } else if (r.itcEligible()) {
                    itcAvailable = itcAvailable.add(r.taxableValue(), r.igst(), r.cgst(), r.sgst(), r.cess());
                } else {
                    itcIneligible = itcIneligible.add(r.taxableValue(), r.igst(), r.cgst(), r.sgst(), r.cess());
                }
            }
        }

        // 4C = 4A − 4B, floored at zero per head
        TaxAmounts netItcAvailable = new TaxAmounts(BigDecimal.ZERO,
                floor(itcAvailable.igst().subtract(itcReversed.igst())),
                floor(itcAvailable.cgst().subtract(itcReversed.cgst())),
                floor(itcAvailable.sgst().subtract(itcReversed.sgst())),
                floor(itcAvailable.cess().subtract(itcReversed.cess())));

        // 6: net tax payable = 3.1(a) − 4C, floored at zero per head
        TaxAmounts netPayable = new TaxAmounts(BigDecimal.ZERO,
                floor(outwardTaxable.igst().subtract(netItcAvailable.igst())),
                floor(outwardTaxable.cgst().subtract(netItcAvailable.cgst())),
                floor(outwardTaxable.sgst().subtract(netItcAvailable.sgst())),
                floor(outwardTaxable.cess().subtract(netItcAvailable.cess())));

        GstReconciliation recon = reconcile(from, to, outwardTaxable, netItcAvailable);

        return new Gstr3bDto(gst.companyGstin(), from, to,
                outwardTaxable, inwardRcm, section32,
                itcAvailable, itcReversed, netItcAvailable,
                itcIneligible, netPayable, recon);
    }

    // ── GL reconciliation ────────────────────────────────────────────────────────

    private GstReconciliation reconcile(LocalDate from, LocalDate to,
                                        TaxAmounts outwardTaxable, TaxAmounts netItcAvailable) {
        BigDecimal ledgerOutput = signedMovement(from, to, OUTPUT_CODES, true);
        BigDecimal ledgerItc    = signedMovement(from, to, INPUT_CODES, false);
        BigDecimal returnOutput = outwardTaxable.totalTax();
        BigDecimal returnItc    = netItcAvailable.totalTax();
        BigDecimal outputVar    = ledgerOutput.subtract(returnOutput);
        BigDecimal itcVar       = ledgerItc.subtract(returnItc);
        boolean ties = outputVar.abs().compareTo(TIE_TOLERANCE) <= 0
                && itcVar.abs().compareTo(TIE_TOLERANCE) <= 0;
        return new GstReconciliation(ledgerOutput, returnOutput, outputVar, ledgerItc, returnItc, itcVar, ties);
    }

    private BigDecimal signedMovement(LocalDate from, LocalDate to, List<String> codes, boolean creditPositive) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : voucherLineRepo.movementByCodeInRange(from, to, codes)) {
            BigDecimal dr = (BigDecimal) row[1];
            BigDecimal cr = (BigDecimal) row[2];
            total = total.add(creditPositive ? cr.subtract(dr) : dr.subtract(cr));
        }
        return total;
    }

    private BigDecimal floor(BigDecimal v) {
        return v.signum() < 0 ? BigDecimal.ZERO : v;
    }

    // ── Excel (matches actual GSTR-3B form layout) ────────────────────────────

    @Override
    public byte[] toExcel(Gstr3bDto g) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("GSTR-3B");

            CellStyle bold   = style(wb, true, false, IndexedColors.WHITE);
            CellStyle header = style(wb, true, false, IndexedColors.GREY_25_PERCENT);
            CellStyle sec    = style(wb, true, false, IndexedColors.LIGHT_CORNFLOWER_BLUE);
            CellStyle tieGood = style(wb, true, false, IndexedColors.LIGHT_GREEN);
            CellStyle tieBad  = style(wb, true, false, IndexedColors.ROSE);
            CellStyle normal  = style(wb, false, false, IndexedColors.WHITE);

            int r = 0;

            // ── Title ──────────────────────────────────────────────────────────
            r = titleRow(sh, r, "FORM GSTR-3B", bold, 6);
            r = titleRow(sh, r, "[See rule 61(5)]", normal, 6);
            r++;

            Row meta = sh.createRow(r++);
            cell(meta, 0, "GSTIN", header); cell(meta, 1, s(g.gstin()), normal);
            cell(meta, 3, "Year", header);  cell(meta, 4, String.valueOf(g.from().getYear()), normal);
            Row meta2 = sh.createRow(r++);
            cell(meta2, 3, "Month", header);
            cell(meta2, 4, g.from().format(DateTimeFormatter.ofPattern("MMMM")), normal);
            r++;

            // ── Section 3.1 ────────────────────────────────────────────────────
            r = sectionTitle(sh, r, "3.1 Details of Outward Supplies and Inward Supplies Liable to Reverse Charge", sec, 6);
            r = colHeader(sh, r, header, "Nature of Supplies", "Total Taxable Value", "Integrated Tax", "Central Tax", "State/UT Tax", "Cess");
            r = taxRow(sh, r, normal, "(a) Outward taxable supplies (other than zero rated, nil rated and exempted)", g.outwardTaxable());
            r = taxRowBlank(sh, r, normal, "(b) Outward taxable supplies (zero rated)");
            r = taxRowBlank(sh, r, normal, "(c) Other outward supplies (Nil rated, exempted)");
            r = taxRow(sh, r, normal, "(d) Inward supplies (liable to reverse charge)", g.inwardRcm());
            r = taxRowBlank(sh, r, normal, "(e) Non-GST outward supplies");
            r++;

            // ── Section 3.2 ────────────────────────────────────────────────────
            r = sectionTitle(sh, r, "3.2 Inter-State Supplies to Unregistered Persons, Composition Taxable Persons and UIN Holders", sec, 6);
            r = colHeader(sh, r, header, "Place of Supply (State/UT)", "Total Taxable Value", "Amount of Integrated Tax", "", "", "");
            if (g.section32().isEmpty()) {
                Row blank = sh.createRow(r++);
                cell(blank, 0, "—", normal);
            } else {
                for (Section32Row row : g.section32()) {
                    Row er = sh.createRow(r++);
                    cell(er, 0, GstState.labelFor(row.stateCode()), normal);
                    num(er, 1, row.taxableValue(), normal);
                    num(er, 2, row.igst(), normal);
                }
            }
            r++;

            // ── Section 4 ──────────────────────────────────────────────────────
            r = sectionTitle(sh, r, "4. Eligible ITC", sec, 6);
            r = colHeader(sh, r, header, "Details", "Integrated Tax", "Central Tax", "State/UT Tax", "Cess", "");

            r = subTitle(sh, r, "(A) ITC Available (whether in full or part)", bold);
            r = blankSubRow(sh, r, normal, "  (1) Import of goods");
            r = blankSubRow(sh, r, normal, "  (2) Import of services");
            r = blankSubRow(sh, r, normal, "  (3) Inward supplies liable to reverse charge (other than 1 & 2 above)");
            r = blankSubRow(sh, r, normal, "  (4) Inward supplies from ISD");
            r = itcRow(sh, r, normal, "  (5) All other ITC", g.itcAvailable());

            r = subTitle(sh, r, "(B) ITC Reversed", bold);
            r = blankSubRow(sh, r, normal, "  (1) As per rules 42 & 43 of CGST Rules");
            r = itcRow(sh, r, normal, "  (2) Others (Debit Notes)", g.itcReversed());

            r = itcRow(sh, r, bold, "(C) Net ITC Available (A) − (B)", g.netItcAvailable());

            r = subTitle(sh, r, "(D) Ineligible ITC", bold);
            r = blankSubRow(sh, r, normal, "  (1) As per section 17(5)");
            r = itcRow(sh, r, normal, "  (2) Others", g.itcIneligible());
            r++;

            // ── Section 5.1 ────────────────────────────────────────────────────
            r = sectionTitle(sh, r, "5.1 Payment of Tax (Interest / Late Fee) — not applicable", sec, 6);
            r++;

            // ── Section 6 ──────────────────────────────────────────────────────
            r = sectionTitle(sh, r, "6. Payment of Tax", sec, 6);
            r = colHeader(sh, r, header, "Description", "Tax Payable", "ITC Utilized", "Tax Paid in Cash", "", "");

            TaxAmounts payable = g.netTaxPayable();
            TaxAmounts itc     = g.netItcAvailable();
            r = payRow(sh, r, normal, "Integrated Tax",
                    payable.igst(), min(itc.igst(), outwardOf(g).igst()), payable.igst());
            r = payRow(sh, r, normal, "Central Tax",
                    payable.cgst(), min(itc.cgst(), outwardOf(g).cgst()), payable.cgst());
            r = payRow(sh, r, normal, "State/UT Tax",
                    payable.sgst(), min(itc.sgst(), outwardOf(g).sgst()), payable.sgst());
            r = payRow(sh, r, normal, "Cess",
                    payable.cess(), min(itc.cess(), outwardOf(g).cess()), payable.cess());
            r++;

            // ── GL Reconciliation (internal) ───────────────────────────────────
            r = sectionTitle(sh, r, "GL Reconciliation (internal — not part of GSTR-3B form)", sec, 6);
            GstReconciliation rec = g.reconciliation();
            r = reconRow(sh, r, normal, "Output tax — ledger (accounts 9010-9013)", rec.ledgerOutput(), null);
            r = reconRow(sh, r, normal, "Output tax — return 3.1(a)", rec.returnOutput(), null);
            r = reconRow(sh, r, bold,   "Output variance", rec.outputVariance(), null);
            r = reconRow(sh, r, normal, "ITC — ledger (accounts 6020-6023)", rec.ledgerItc(), null);
            r = reconRow(sh, r, normal, "ITC — return 4(C)", rec.returnItc(), null);
            r = reconRow(sh, r, bold,   "ITC variance", rec.itcVariance(), null);

            Row tieRow = sh.createRow(r);
            Cell tieCell = tieRow.createCell(0);
            boolean ties = rec.tiesOut();
            tieCell.setCellValue(ties ? "TIES TO GL ✓" : "VARIANCE — DOES NOT TIE ✗");
            tieCell.setCellStyle(ties ? tieGood : tieBad);

            for (int i = 0; i < 6; i++) sh.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate GSTR-3B Excel", e);
        }
    }

    // ── Excel row helpers ──────────────────────────────────────────────────────

    private int titleRow(Sheet sh, int r, String text, CellStyle style, int span) {
        Row row = sh.createRow(r);
        Cell c = row.createCell(0);
        c.setCellValue(text);
        c.setCellStyle(style);
        return r + 1;
    }

    private int sectionTitle(Sheet sh, int r, String text, CellStyle style, int span) {
        Row row = sh.createRow(r);
        Cell c = row.createCell(0);
        c.setCellValue(text);
        c.setCellStyle(style);
        for (int i = 1; i < span; i++) row.createCell(i).setCellStyle(style);
        return r + 1;
    }

    private int subTitle(Sheet sh, int r, String text, CellStyle style) {
        Row row = sh.createRow(r);
        Cell c = row.createCell(0);
        c.setCellValue(text);
        c.setCellStyle(style);
        return r + 1;
    }

    private int colHeader(Sheet sh, int r, CellStyle style, String... cols) {
        Row row = sh.createRow(r);
        for (int i = 0; i < cols.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(style);
        }
        return r + 1;
    }

    /** 3.1 section row: label | taxable | igst | cgst | sgst | cess */
    private int taxRow(Sheet sh, int r, CellStyle style, String label, TaxAmounts a) {
        Row row = sh.createRow(r);
        cell(row, 0, label, style);
        num(row, 1, a.taxableValue(), style);
        num(row, 2, a.igst(), style);
        num(row, 3, a.cgst(), style);
        num(row, 4, a.sgst(), style);
        num(row, 5, a.cess(), style);
        return r + 1;
    }

    private int taxRowBlank(Sheet sh, int r, CellStyle style, String label) {
        Row row = sh.createRow(r);
        cell(row, 0, label, style);
        return r + 1;
    }

    /** Section 4 ITC row: label | igst | cgst | sgst | cess (no taxable column) */
    private int itcRow(Sheet sh, int r, CellStyle style, String label, TaxAmounts a) {
        Row row = sh.createRow(r);
        cell(row, 0, label, style);
        num(row, 1, a.igst(), style);
        num(row, 2, a.cgst(), style);
        num(row, 3, a.sgst(), style);
        num(row, 4, a.cess(), style);
        return r + 1;
    }

    private int blankSubRow(Sheet sh, int r, CellStyle style, String label) {
        Row row = sh.createRow(r);
        cell(row, 0, label, style);
        return r + 1;
    }

    /** Section 6 payment row: label | payable | itc | cash */
    private int payRow(Sheet sh, int r, CellStyle style, String label,
                       BigDecimal payable, BigDecimal itcUsed, BigDecimal cash) {
        Row row = sh.createRow(r);
        cell(row, 0, label, style);
        num(row, 1, payable, style);
        num(row, 2, itcUsed, style);
        num(row, 3, cash, style);
        return r + 1;
    }

    private int reconRow(Sheet sh, int r, CellStyle style, String label, BigDecimal value, CellStyle ignore) {
        Row row = sh.createRow(r);
        Cell c = row.createCell(0);
        c.setCellValue(label);
        c.setCellStyle(style);
        num(row, 1, value, style);
        return r + 1;
    }

    private void cell(Row row, int col, String val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        c.setCellStyle(style);
    }

    private void num(Row row, int col, BigDecimal val, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val.doubleValue() : 0.0);
        c.setCellStyle(style);
    }

    private CellStyle style(Workbook wb, boolean bold, boolean wrap, IndexedColors bg) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(bold);
        s.setFont(f);
        s.setWrapText(wrap);
        s.setFillForegroundColor(bg.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    // ── JSON (GSTN offline-tool 3B schema) ───────────────────────────────────────

    @Override
    public byte[] toJson(Gstr3bDto g) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("gstin", g.gstin());
        root.put("ret_period", String.format("%02d%d", g.to().getMonthValue(), g.to().getYear()));

        Map<String, Object> supDetails = new LinkedHashMap<>();
        supDetails.put("osup_det", supRow(g.outwardTaxable()));
        supDetails.put("isup_rev", supRow(g.inwardRcm()));

        // 3.2 inter-state to unregistered
        if (!g.section32().isEmpty()) {
            List<Map<String, Object>> interState = new ArrayList<>();
            for (Section32Row sr : g.section32()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("pos", sr.stateCode());
                m.put("txval", money(sr.taxableValue()));
                m.put("iamt", money(sr.igst()));
                interState.add(m);
            }
            supDetails.put("inter_sup", interState);
        }
        root.put("sup_details", supDetails);

        Map<String, Object> itcElg = new LinkedHashMap<>();
        itcElg.put("itc_avl", List.of(itcRow("OTH", g.itcAvailable())));
        itcElg.put("itc_rev", List.of(itcRow("OTH", g.itcReversed())));
        itcElg.put("net_itc_avl", itcRow("OTH", g.netItcAvailable()));
        itcElg.put("itc_inelg", List.of(itcRow("OTH", g.itcIneligible())));
        root.put("itc_elg", itcElg);

        try {
            return objectMapper.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialise GSTR-3B JSON", e);
        }
    }

    // ── Value helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> supRow(TaxAmounts a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("txval", money(a.taxableValue()));
        m.put("iamt",  money(a.igst()));
        m.put("camt",  money(a.cgst()));
        m.put("samt",  money(a.sgst()));
        m.put("csamt", money(a.cess()));
        return m;
    }

    private Map<String, Object> itcRow(String ty, TaxAmounts a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ty",    ty);
        m.put("iamt",  money(a.igst()));
        m.put("camt",  money(a.cgst()));
        m.put("samt",  money(a.sgst()));
        m.put("csamt", money(a.cess()));
        return m;
    }

    /** outward tax heads (for ITC utilization display — IGST ITC used against IGST payable etc.) */
    private TaxAmounts outwardOf(Gstr3bDto g) { return g.outwardTaxable(); }

    private BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.min(b);
    }

    private static BigDecimal money(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static String s(String v) {
        return v != null ? v : "";
    }
}
