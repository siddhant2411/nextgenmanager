package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.common.service.PdfPageFit;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.company.service.DocumentBrandingService;
import com.nextgenmanager.nextgenmanager.purchase.service.AmountInWords;
import com.nextgenmanager.nextgenmanager.sales.exception.SalesOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesPayment;
import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesPaymentRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoicePdfService {

    /** Item rows on a page when everything fits, which it does on most documents. */
    private static final int ITEMS_PER_PAGE = 10;

    /** Below this the page is given up on: something other than the item rows is what does not fit. */
    private static final int MIN_ITEMS_PER_PAGE = 2;

    private final TemplateEngine templateEngine;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesPaymentRepository salesPaymentRepository;
    private final CompanyDetailsRepository companyDetailsRepository;
    private final DocumentBrandingService documentBrandingService;

    public byte[] generateInvoicePdf(Long id) {
        return renderFitted("invoice/invoice", buildContext(id, true));
    }

    public byte[] generateOrderAcknowledgementPdf(Long id) {
        return renderFitted("invoice/order-acknowledgement", buildContext(id, false));
    }

    public byte[] generateProformaInvoicePdf(Long id) {
        return renderFitted("invoice/proforma-invoice", buildContext(id, false));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Context buildContext(Long id, boolean includePayments) {
        SalesOrder salesOrder = salesOrderRepository.findById(id)
                .orElseThrow(() -> new SalesOrderNotFoundException(id));

        CompanyDetails company = companyDetailsRepository.findAll().stream()
                .findFirst().orElse(new CompanyDetails());

        String companyAddress = Stream.of(company.getStreet1(), company.getStreet2(), company.getCity())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
        if (company.getPinCode() != null && !company.getPinCode().isBlank())
            companyAddress += (companyAddress.isEmpty() ? "" : " - ") + company.getPinCode();
        if (company.getState() != null && !company.getState().isBlank())
            companyAddress += (companyAddress.isEmpty() ? "" : ", ") + company.getState();

        Context ctx = new Context();
        ctx.setVariable("brand", documentBrandingService.current());
        ctx.setVariable("salesOrder", salesOrder);
        ctx.setVariable("company", company);
        ctx.setVariable("companyAddress", companyAddress);
        ctx.setVariable("billTo", SalesParties.billTo(salesOrder));
        ctx.setVariable("shipTo", SalesParties.shipTo(salesOrder));
        ctx.setVariable("TaxType", TaxType.class);
        ctx.setVariable("amountInWords", AmountInWords.convert(salesOrder.getTotalPayableAmount()));

        BigDecimal cgstAmt  = salesOrder.getCgstAmount()  != null ? salesOrder.getCgstAmount()  : BigDecimal.ZERO;
        BigDecimal sgstAmt  = salesOrder.getSgstAmount()  != null ? salesOrder.getSgstAmount()  : BigDecimal.ZERO;
        BigDecimal igstAmt  = salesOrder.getIgstAmount()  != null ? salesOrder.getIgstAmount()  : BigDecimal.ZERO;
        BigDecimal taxableVal = salesOrder.getTaxableValue() != null ? salesOrder.getTaxableValue() : BigDecimal.ZERO;
        BigDecimal totalTax = cgstAmt.add(sgstAmt).add(igstAmt);
        BigDecimal effectiveTaxPct = taxableVal.signum() > 0
                ? totalTax.multiply(BigDecimal.valueOf(100)).divide(taxableVal, 2, RoundingMode.HALF_UP).stripTrailingZeros()
                : BigDecimal.ZERO;
        BigDecimal effectiveHalfTaxPct = effectiveTaxPct.signum() > 0
                ? effectiveTaxPct.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP).stripTrailingZeros()
                : BigDecimal.ZERO;
        ctx.setVariable("effectiveTaxPct", effectiveTaxPct);
        ctx.setVariable("effectiveHalfTaxPct", effectiveHalfTaxPct);

        if (includePayments) {
            Long orderId = salesOrder.getId();
            List<SalesPayment> payments = salesPaymentRepository
                    .findBySalesOrderIdOrderByPaymentDateAsc(orderId);
            BigDecimal totalPaid = salesPaymentRepository.sumAmountBySalesOrderId(orderId);
            BigDecimal payable = salesOrder.getTotalPayableAmount() != null
                    ? salesOrder.getTotalPayableAmount() : BigDecimal.ZERO;
            ctx.setVariable("payments", payments);
            ctx.setVariable("totalPaid", totalPaid);
            ctx.setVariable("balanceDue", payable.subtract(totalPaid).max(BigDecimal.ZERO));
        }
        return ctx;
    }

    /**
     * Renders with as many item rows per page as actually fit.
     *
     * <p>These three documents draw each page as one fixed-height box and close the last one with
     * totals, bank details and a signature. Whether that last page still fits depends on things no
     * constant can know: how tall the letterhead is, how many payments are listed, whether the
     * address or the remarks wrap. So the page is laid out and counted. If the PDF has more pages
     * than there are boxes, the tail of a box has spilled onto a sheet of its own; a row is taken
     * off every page and it is laid out again.
     */
    private byte[] renderFitted(String template, Context ctx) {
        List<?> items = ((SalesOrder) ctx.getVariable("salesOrder")).getItems();
        for (int rowsPerPage = ITEMS_PER_PAGE; ; rowsPerPage--) {
            List<List<Object>> itemPages = paginateItems(items, rowsPerPage);
            ctx.setVariable("rowsPerPage", rowsPerPage);
            ctx.setVariable("itemPages", itemPages);
            byte[] pdf = render(template, ctx);
            if (rowsPerPage <= MIN_ITEMS_PER_PAGE || PdfPageFit.pageCount(pdf) <= itemPages.size()) {
                return pdf;
            }
        }
    }

    private List<List<Object>> paginateItems(List<?> items, int rowsPerPage) {
        List<List<Object>> pages = new ArrayList<>();
        int total = (items != null) ? items.size() : 0;
        int pageCount = Math.max(1, (int) Math.ceil((double) total / rowsPerPage));
        for (int p = 0; p < pageCount; p++) {
            List<Object> page = new ArrayList<>();
            for (int i = 0; i < rowsPerPage; i++) {
                int idx = p * rowsPerPage + i;
                page.add(idx < total ? items.get(idx) : null);
            }
            pages.add(page);
        }
        return pages;
    }

    private byte[] render(String template, Context ctx) {
        String html = templateEngine.process(template, ctx)
                .replace("&nbsp;", "&#160;");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF: " + template, e);
        }
        return out.toByteArray();
    }
}
