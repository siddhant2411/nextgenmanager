package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import com.nextgenmanager.nextgenmanager.sales.repository.DeliveryNoteRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
public class DeliveryNotePdfService {

    private final TemplateEngine templateEngine;
    private final DeliveryNoteRepository deliveryNoteRepository;
    private final CompanyDetailsRepository companyDetailsRepository;

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        DeliveryNote dn = deliveryNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery Note not found: " + id));

        CompanyDetails company = companyDetailsRepository.findAll().stream().findFirst()
                .orElse(new CompanyDetails());

        Context context = new Context();
        context.setVariable("dn", dn);
        context.setVariable("company", company);

        String html = templateEngine.process("invoice/delivery_note", context)
                .replace("&nbsp;", "&#160;");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new RuntimeException("Error generating Delivery Note PDF", e);
        }
        return out.toByteArray();
    }
}
