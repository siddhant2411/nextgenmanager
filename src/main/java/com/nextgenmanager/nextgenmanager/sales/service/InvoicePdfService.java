package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class InvoicePdfService {

    private final TemplateEngine templateEngine;

    public InvoicePdfService(TemplateEngine templateEngine, SalesOrderRepository salesOrderRepository) {
        this.templateEngine = templateEngine;
        this.salesOrderRepository = salesOrderRepository;
    }

    @Autowired
    private final SalesOrderRepository salesOrderRepository;

    public byte[] generateInvoicePdf(Long id) {
        // Context for Thymeleaf
        SalesOrder salesOrder = salesOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        Context context = new Context();
        context.setVariable("salesOrder", salesOrder);

        // Extra variables for template
        context.setVariable("companyName", "Process Equipment Corporation");
        context.setVariable("companyAddress", "123 Street, Ahmedabad");
        context.setVariable("companyEmail", "info@procequip.com");
        context.setVariable("gstNo", "24ARJPM1573G1ZT");

        context.setVariable("bankName", "Process Equipment Corporation");
        context.setVariable("bankBank", "Canara Bank");
        context.setVariable("bankAccount", "028210102655");
        context.setVariable("bankIfsc", "CNBK02821010");
        context.setVariable("TaxType", TaxType.class);

        // Amount in words
        context.setVariable("amountInWords", convertAmountToWords(salesOrder.getNetAmount()));

        // Render HTML
        String htmlContent = templateEngine.process("/invoice/invoice", context);

        // Convert HTML → PDF
        // Sanitize HTML to replace undefined entities like &nbsp;
        htmlContent = htmlContent.replace("&nbsp;", "&#160;");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(htmlContent, null);
        builder.toStream(outputStream);

        try {
            builder.run();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF", e);
        }

        return outputStream.toByteArray();
    }

    private String convertAmountToWords(BigDecimal amount) {
        if (amount == null) return "";
        long value = amount.longValue();
        // Basic converter (you can replace with Apache Commons / ICU4J)
        return NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(value);
    }
}
