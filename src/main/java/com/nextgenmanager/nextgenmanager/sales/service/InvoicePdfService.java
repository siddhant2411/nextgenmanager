package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
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

    private static final int ITEMS_PER_PAGE = 10;

    private final TemplateEngine templateEngine;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesPaymentRepository salesPaymentRepository;
    private final CompanyDetailsRepository companyDetailsRepository;

    public byte[] generateInvoicePdf(Long id) {
        return render("invoice/invoice", buildContext(id, true));
    }

    public byte[] generateOrderAcknowledgementPdf(Long id) {
        return render("invoice/order-acknowledgement", buildContext(id, false));
    }

    public byte[] generateProformaInvoicePdf(Long id) {
        return render("invoice/proforma-invoice", buildContext(id, false));
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

        List<ContactAddress> addresses = salesOrder.getCustomer() != null
                ? salesOrder.getCustomer().getAddresses() : List.of();
        ContactAddress addr = addresses.stream()
                .filter(ContactAddress::isDefault)
                .findFirst()
                .orElseGet(() -> addresses.isEmpty() ? null : addresses.get(0));
        String billingAddress = addr != null
                ? Stream.of(addr.getStreet1(), addr.getStreet2(), addr.getCity(), addr.getState())
                        .filter(s -> s != null && !s.isBlank())
                        .collect(Collectors.joining(", "))
                        + (addr.getPinCode() != null && !addr.getPinCode().isBlank() ? " - " + addr.getPinCode() : "")
                : "";

        Context ctx = new Context();
        ctx.setVariable("salesOrder", salesOrder);
        ctx.setVariable("company", company);
        ctx.setVariable("companyAddress", companyAddress);
        ctx.setVariable("billingAddress", billingAddress);
        ctx.setVariable("itemPages", paginateItems(salesOrder.getItems()));
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

    private List<List<Object>> paginateItems(List<?> items) {
        List<List<Object>> pages = new ArrayList<>();
        int total = (items != null) ? items.size() : 0;
        int pageCount = Math.max(1, (int) Math.ceil((double) total / ITEMS_PER_PAGE));
        for (int p = 0; p < pageCount; p++) {
            List<Object> page = new ArrayList<>();
            for (int i = 0; i < ITEMS_PER_PAGE; i++) {
                int idx = p * ITEMS_PER_PAGE + i;
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
