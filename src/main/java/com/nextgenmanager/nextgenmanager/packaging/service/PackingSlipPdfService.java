package com.nextgenmanager.nextgenmanager.packaging.service;

import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.company.service.DocumentBrandingService;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip;
import com.nextgenmanager.nextgenmanager.packaging.repository.PackingSlipRepository;
import com.nextgenmanager.nextgenmanager.sales.service.SalesParties;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

/** The Packing List PDF — the real document behind what used to be only the PACKING_LIST enum label. */
@Service
@RequiredArgsConstructor
public class PackingSlipPdfService {

    private final TemplateEngine templateEngine;
    private final PackingSlipRepository packingSlipRepository;
    private final CompanyDetailsRepository companyDetailsRepository;
    private final DocumentBrandingService documentBrandingService;

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        PackingSlip slip = packingSlipRepository.findLiveById(id)
                .orElseThrow(() -> new RuntimeException("Packing slip not found: " + id));

        CompanyDetails company = companyDetailsRepository.findAll().stream().findFirst()
                .orElse(new CompanyDetails());

        Context context = new Context();
        context.setVariable("brand", documentBrandingService.current());
        context.setVariable("slip", slip);
        context.setVariable("company", company);
        context.setVariable("billTo", SalesParties.billTo(slip.getSalesOrder()));
        context.setVariable("shipTo", SalesParties.shipTo(slip.getSalesOrder()));

        String html = templateEngine.process("invoice/packing_list", context)
                .replace("&nbsp;", "&#160;");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new RuntimeException("Error generating Packing List PDF", e);
        }
        return out.toByteArray();
    }
}
