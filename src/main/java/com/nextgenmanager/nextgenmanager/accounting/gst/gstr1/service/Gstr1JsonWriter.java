package com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextgenmanager.nextgenmanager.accounting.gst.gstr1.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.gst.hsn.dto.HsnSummaryRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Serialises a {@link Gstr1Dto} into the GSTN offline-tool JSON schema. Field names ({@code inum},
 * {@code idt}, {@code txval}, {@code iamt}, …) and the {@code dd-MM-yyyy} date format match the tool
 * exactly so the output can be imported without transformation. EXP/SEZ sections are omitted (deferred).
 */
@Component
@RequiredArgsConstructor
public class Gstr1JsonWriter {

    private static final DateTimeFormatter GSTN_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ObjectMapper objectMapper;

    public byte[] write(Gstr1Dto g) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("gstin", g.gstin());
        root.put("fp", filingPeriod(g.to()));
        if (!g.b2b().isEmpty()) root.put("b2b", b2b(g.b2b()));
        if (!g.b2cl().isEmpty()) root.put("b2cl", b2cl(g.b2cl()));
        if (!g.b2cs().isEmpty()) root.put("b2cs", b2cs(g.b2cs()));
        if (!g.cdnr().isEmpty()) root.put("cdnr", cdnr(g.cdnr()));
        if (!g.cdnur().isEmpty()) root.put("cdnur", cdnur(g.cdnur()));
        if (g.hsn() != null && !g.hsn().rows().isEmpty()) root.put("hsn", hsn(g.hsn().rows()));
        if (!g.docs().isEmpty()) root.put("doc_issue", docIssue(g.docs()));
        try {
            return objectMapper.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialise GSTR-1 JSON", e);
        }
    }

    // ── B2B: grouped by recipient GSTIN ──────────────────────────────────────────

    private List<Map<String, Object>> b2b(List<B2bInvoice> invoices) {
        Map<String, List<Map<String, Object>>> byCtin = new LinkedHashMap<>();
        for (B2bInvoice inv : invoices) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("inum", inv.invoiceNo());
            m.put("idt", date(inv.invoiceDate()));
            m.put("val", money(inv.invoiceValue()));
            m.put("pos", inv.placeOfSupply());
            m.put("rchrg", inv.reverseCharge() ? "Y" : "N");
            m.put("inv_typ", "R");
            m.put("itms", itms(inv.items()));
            byCtin.computeIfAbsent(inv.ctin(), k -> new ArrayList<>()).add(m);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byCtin.forEach((ctin, inv) -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("ctin", ctin);
            g.put("inv", inv);
            out.add(g);
        });
        return out;
    }

    // ── B2CL: grouped by place of supply ─────────────────────────────────────────

    private List<Map<String, Object>> b2cl(List<B2clInvoice> invoices) {
        Map<String, List<Map<String, Object>>> byPos = new LinkedHashMap<>();
        for (B2clInvoice inv : invoices) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("inum", inv.invoiceNo());
            m.put("idt", date(inv.invoiceDate()));
            m.put("val", money(inv.invoiceValue()));
            m.put("itms", itms(inv.items()));
            byPos.computeIfAbsent(inv.placeOfSupply(), k -> new ArrayList<>()).add(m);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byPos.forEach((pos, inv) -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("pos", pos);
            g.put("inv", inv);
            out.add(g);
        });
        return out;
    }

    // ── B2CS: flat consolidated rows ─────────────────────────────────────────────

    private List<Map<String, Object>> b2cs(List<B2csRow> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (B2csRow r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sply_ty", r.igst() != null && r.igst().signum() != 0 ? "INTER" : "INTRA");
            m.put("typ", r.type());
            m.put("pos", r.placeOfSupply());
            m.put("rt", rate(r.rate()));
            m.put("txval", money(r.taxableValue()));
            m.put("iamt", money(r.igst()));
            m.put("camt", money(r.cgst()));
            m.put("samt", money(r.sgst()));
            m.put("csamt", money(r.cess()));
            out.add(m);
        }
        return out;
    }

    // ── CDNR: grouped by recipient GSTIN ─────────────────────────────────────────

    private List<Map<String, Object>> cdnr(List<CdnRow> notes) {
        Map<String, List<Map<String, Object>>> byCtin = new LinkedHashMap<>();
        for (CdnRow n : notes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ntty", n.noteType());
            m.put("nt_num", n.noteNo());
            m.put("nt_dt", date(n.noteDate()));
            m.put("val", money(n.noteValue()));
            m.put("pos", n.placeOfSupply());
            m.put("rchrg", "N");
            m.put("inv_typ", "R");
            m.put("itms", itms(n.items()));
            byCtin.computeIfAbsent(n.ctin(), k -> new ArrayList<>()).add(m);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byCtin.forEach((ctin, nt) -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("ctin", ctin);
            g.put("nt", nt);
            out.add(g);
        });
        return out;
    }

    // ── CDNUR: flat ──────────────────────────────────────────────────────────────

    private List<Map<String, Object>> cdnur(List<CdnRow> notes) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CdnRow n : notes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("typ", "B2CL");
            m.put("ntty", n.noteType());
            m.put("nt_num", n.noteNo());
            m.put("nt_dt", date(n.noteDate()));
            m.put("val", money(n.noteValue()));
            m.put("pos", n.placeOfSupply());
            m.put("itms", itms(n.items()));
            out.add(m);
        }
        return out;
    }

    // ── HSN ──────────────────────────────────────────────────────────────────────

    private Map<String, Object> hsn(List<HsnSummaryRow> rows) {
        List<Map<String, Object>> data = new ArrayList<>();
        int num = 1;
        for (HsnSummaryRow r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("num", num++);
            m.put("hsn_sc", r.hsnCode());
            m.put("desc", r.description());
            m.put("uqc", r.uqc());
            m.put("qty", money(r.quantity()));
            m.put("rt", rate(r.rate()));
            m.put("txval", money(r.taxableValue()));
            m.put("iamt", money(r.igst()));
            m.put("camt", money(r.cgst()));
            m.put("samt", money(r.sgst()));
            m.put("csamt", money(r.cess()));
            m.put("val", money(r.totalValue()));
            data.add(m);
        }
        Map<String, Object> hsn = new LinkedHashMap<>();
        hsn.put("data", data);
        return hsn;
    }

    // ── Documents issued ─────────────────────────────────────────────────────────

    private Map<String, Object> docIssue(List<DocSeries> series) {
        List<Map<String, Object>> docDet = new ArrayList<>();
        for (DocSeries s : series) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("num", 1);
            doc.put("from", s.fromNo());
            doc.put("to", s.toNo());
            doc.put("totnum", s.totalCount());
            doc.put("cancel", s.cancelled());
            doc.put("net_issue", s.totalCount() - s.cancelled());

            Map<String, Object> det = new LinkedHashMap<>();
            det.put("doc_num", docNum(s.nature()));
            det.put("docs", List.of(doc));
            docDet.add(det);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("doc_det", docDet);
        return root;
    }

    private int docNum(String nature) {
        return "Credit Notes".equals(nature) ? 5 : 1;   // 1 = invoices for outward supply, 5 = credit note
    }

    // ── Items (itm_det) ──────────────────────────────────────────────────────────

    private List<Map<String, Object>> itms(List<RateLine> lines) {
        List<Map<String, Object>> out = new ArrayList<>();
        int num = 1;
        for (RateLine l : lines) {
            Map<String, Object> det = new LinkedHashMap<>();
            det.put("rt", rate(l.rate()));
            det.put("txval", money(l.taxableValue()));
            det.put("iamt", money(l.igst()));
            det.put("camt", money(l.cgst()));
            det.put("samt", money(l.sgst()));
            det.put("csamt", money(l.cess()));

            Map<String, Object> itm = new LinkedHashMap<>();
            itm.put("num", num++);
            itm.put("itm_det", det);
            out.add(itm);
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private String filingPeriod(LocalDate to) {
        return String.format("%02d%d", to.getMonthValue(), to.getYear());
    }

    private String date(LocalDate d) {
        return d != null ? d.format(GSTN_DATE) : null;
    }

    private BigDecimal money(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Rate is an integer/decimal percentage in the offline tool (e.g. 18, 2.5). */
    private BigDecimal rate(BigDecimal v) {
        return v != null ? v.stripTrailingZeros() : BigDecimal.ZERO;
    }
}
