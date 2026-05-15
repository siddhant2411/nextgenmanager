package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.purchase.service.AmountInWords;
import com.nextgenmanager.nextgenmanager.sales.exception.SalesOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.TaxType;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private final TemplateEngine templateEngine;
    private final SalesOrderRepository salesOrderRepository;

    public byte[] generateInvoicePdf(Long id) {
        SalesOrder salesOrder = salesOrderRepository.findById(id)
                .orElseThrow(() -> new SalesOrderNotFoundException(id));

        Context context = new Context();
        context.setVariable("salesOrder", salesOrder);
        context.setVariable("companyName", "Process Equipment Corporation");
        context.setVariable("companyAddress", "123 Street, Ahmedabad");
        context.setVariable("companyEmail", "info@procequip.com");
        context.setVariable("gstNo", "24ARJPM1573G1ZT");
        context.setVariable("bankName", "Process Equipment Corporation");
        context.setVariable("bankBank", "Canara Bank");
        context.setVariable("bankAccount", "028210102655");
        context.setVariable("bankIfsc", "CNBK02821010");
        context.setVariable("TaxType", TaxType.class);
        context.setVariable("amountInWords", AmountInWords.convert(salesOrder.getTotalPayableAmount()));

        String html = templateEngine.process("invoice/invoice", context)
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
}
