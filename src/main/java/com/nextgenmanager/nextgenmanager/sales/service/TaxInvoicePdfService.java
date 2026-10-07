package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.company.service.DocumentBrandingService;
import com.nextgenmanager.nextgenmanager.purchase.service.AmountInWords;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;
import com.nextgenmanager.nextgenmanager.sales.repository.TaxInvoiceRepository;
import com.nextgenmanager.nextgenmanager.common.service.PdfPageFit;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaxInvoicePdfService {

    /** The item table is padded with blank rows to this many, as long as that does not add a sheet. */
    private static final int FILLER_ROWS = 8;

    private final TemplateEngine templateEngine;
    private final TaxInvoiceRepository taxInvoiceRepository;
    private final CompanyDetailsRepository companyDetailsRepository;
    private final DocumentBrandingService documentBrandingService;

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        TaxInvoice invoice = taxInvoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + id));

        CompanyDetails company = companyDetailsRepository.findAll().stream().findFirst()
                .orElse(new CompanyDetails());

        Context context = new Context();
        context.setVariable("brand", documentBrandingService.current());
        context.setVariable("invoice", invoice);
        context.setVariable("company", company);
        context.setVariable("amountInWords", AmountInWords.convert(invoice.getTotalPayableAmount()));
        
        context.setVariable("billTo", SalesParties.billTo(invoice));
        context.setVariable("shipTo", SalesParties.shipTo(invoice));

        // Bank Details (Using established hardcoded values for now)
        context.setVariable("bankName", "Canara Bank");
        context.setVariable("bankAccount", "028210102655");
        context.setVariable("bankIfsc", "CNBK02821010");
        context.setVariable("bankBranch", "Ahmedabad Main");
        
        // HSN Summary Calculation
        Map<String, HsnSummary> hsnMap = new HashMap<>();
        invoice.getItems().forEach(item -> {
            String hsn = (item.getInventoryItem() != null && item.getInventoryItem().getHsnCode() != null) 
                    ? item.getInventoryItem().getHsnCode() : "N/A";
            HsnSummary summary = hsnMap.getOrDefault(hsn, new HsnSummary());
            summary.setTaxableValue(summary.getTaxableValue().add(item.getTotalAmount().subtract(
                (item.getCgstAmount() != null ? item.getCgstAmount() : BigDecimal.ZERO)
                .add(item.getSgstAmount() != null ? item.getSgstAmount() : BigDecimal.ZERO)
                .add(item.getIgstAmount() != null ? item.getIgstAmount() : BigDecimal.ZERO)
            )));
            summary.setCgst(summary.getCgst().add(item.getCgstAmount() != null ? item.getCgstAmount() : BigDecimal.ZERO));
            summary.setSgst(summary.getSgst().add(item.getSgstAmount() != null ? item.getSgstAmount() : BigDecimal.ZERO));
            summary.setIgst(summary.getIgst().add(item.getIgstAmount() != null ? item.getIgstAmount() : BigDecimal.ZERO));
            hsnMap.put(hsn, summary);
        });
        context.setVariable("hsnSummary", hsnMap);

        // Derive taxable base from line items (more reliable than stored header field)
        BigDecimal computedTaxable = hsnMap.values().stream()
                .map(HsnSummary::getTaxableValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Fall back to stored taxableValue if line-item derivation is zero (e.g. no items yet)
        BigDecimal invTaxable = computedTaxable.signum() > 0 ? computedTaxable
                : (invoice.getTaxableValue() != null ? invoice.getTaxableValue() : BigDecimal.ZERO);

        BigDecimal invCgst = invoice.getCgstAmount() != null ? invoice.getCgstAmount() : BigDecimal.ZERO;
        BigDecimal invSgst = invoice.getSgstAmount() != null ? invoice.getSgstAmount() : BigDecimal.ZERO;
        BigDecimal invIgst = invoice.getIgstAmount() != null ? invoice.getIgstAmount() : BigDecimal.ZERO;
        BigDecimal invTotalTax = invCgst.add(invSgst).add(invIgst);
        BigDecimal invEffectiveTaxPct = invTaxable.signum() > 0
                ? invTotalTax.multiply(BigDecimal.valueOf(100)).divide(invTaxable, 2, RoundingMode.HALF_UP).stripTrailingZeros()
                : BigDecimal.ZERO;
        BigDecimal invEffectiveHalfTaxPct = invEffectiveTaxPct.signum() > 0
                ? invEffectiveTaxPct.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP).stripTrailingZeros()
                : BigDecimal.ZERO;
        context.setVariable("effectiveTaxPct", invEffectiveTaxPct);
        context.setVariable("effectiveHalfTaxPct", invEffectiveHalfTaxPct);

        return PdfPageFit.withFillerRows(FILLER_ROWS, fillerRows -> {
            context.setVariable("fillerRows", fillerRows);
            return render(context);
        });
    }

    private byte[] render(Context context) {
        String html = templateEngine.process("invoice/tax_invoice_premium", context)
                .replace("&nbsp;", "&#160;");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF", e);
        }
        return out.toByteArray();
    }

    public static class HsnSummary {
        private BigDecimal taxableValue = BigDecimal.ZERO;
        private BigDecimal cgst = BigDecimal.ZERO;
        private BigDecimal sgst = BigDecimal.ZERO;
        private BigDecimal igst = BigDecimal.ZERO;
        
        public BigDecimal getTaxableValue() { return taxableValue; }
        public void setTaxableValue(BigDecimal v) { this.taxableValue = v; }
        public BigDecimal getCgst() { return cgst; }
        public void setCgst(BigDecimal v) { this.cgst = v; }
        public BigDecimal getSgst() { return sgst; }
        public void setSgst(BigDecimal v) { this.sgst = v; }
        public BigDecimal getIgst() { return igst; }
        public void setIgst(BigDecimal v) { this.igst = v; }
        public BigDecimal getTotalTax() { return cgst.add(sgst).add(igst); }
    }
}
