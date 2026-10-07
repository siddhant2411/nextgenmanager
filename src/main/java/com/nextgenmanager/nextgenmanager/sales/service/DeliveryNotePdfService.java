package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.company.service.DocumentBrandingService;
import com.nextgenmanager.nextgenmanager.sales.model.DeliveryNote;
import com.nextgenmanager.nextgenmanager.sales.repository.DeliveryNoteRepository;
import com.nextgenmanager.nextgenmanager.common.service.PdfPageFit;
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

    /** The item table is padded with blank rows to this many, as long as that does not add a sheet. */
    private static final int FILLER_ROWS = 10;

    private final TemplateEngine templateEngine;
    private final DeliveryNoteRepository deliveryNoteRepository;
    private final CompanyDetailsRepository companyDetailsRepository;
    private final DocumentBrandingService documentBrandingService;

    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        DeliveryNote dn = deliveryNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery Note not found: " + id));

        CompanyDetails company = companyDetailsRepository.findAll().stream().findFirst()
                .orElse(new CompanyDetails());

        Context context = new Context();
        context.setVariable("brand", documentBrandingService.current());
        context.setVariable("dn", dn);
        context.setVariable("company", company);
        context.setVariable("billTo", SalesParties.billTo(dn.getSalesOrder()));
        context.setVariable("shipTo", SalesParties.shipTo(dn.getSalesOrder()));

        return PdfPageFit.withFillerRows(FILLER_ROWS, fillerRows -> {
            context.setVariable("fillerRows", fillerRows);
            return render(context);
        });
    }

    private byte[] render(Context context) {
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
