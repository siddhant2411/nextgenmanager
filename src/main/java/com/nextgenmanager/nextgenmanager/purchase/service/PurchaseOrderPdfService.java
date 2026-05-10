package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

@Service
public class PurchaseOrderPdfService {

    private final TemplateEngine templateEngine;
    private final CompanyDetailsRepository companyRepo;

    public PurchaseOrderPdfService(TemplateEngine templateEngine,
                                   CompanyDetailsRepository companyRepo) {
        this.templateEngine = templateEngine;
        this.companyRepo = companyRepo;
    }

    public byte[] generate(PurchaseOrder po) {
        Context ctx = new Context();
        ctx.setVariable("po", po);
        ctx.setVariable("company", companyRepo.findAll().stream().findFirst().orElse(null));
        ctx.setVariable("amountInWords", AmountInWords.convert(po.getGrandTotal()));

        String html = templateEngine.process("/purchase/purchase-order", ctx);
        html = html.replace("&nbsp;", "&#160;");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PO PDF", e);
        }
        return out.toByteArray();
    }
}
