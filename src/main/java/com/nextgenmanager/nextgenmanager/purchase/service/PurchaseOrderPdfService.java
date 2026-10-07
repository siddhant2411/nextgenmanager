package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.company.service.DocumentBrandingService;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.contact.model.ContactPersonDetail;
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
    private final DocumentBrandingService documentBrandingService;

    public PurchaseOrderPdfService(TemplateEngine templateEngine,
                                   CompanyDetailsRepository companyRepo,
                                   DocumentBrandingService documentBrandingService) {
        this.templateEngine = templateEngine;
        this.companyRepo = companyRepo;
        this.documentBrandingService = documentBrandingService;
    }

    /**
     * Where the vendor is, for the vendor block. The address chosen on the order comes first; an
     * order raised without one still prints the vendor's default address, or failing that the first
     * one on file, so the block is never just a name.
     */
    private ContactAddress vendorAddress(PurchaseOrder po) {
        if (po.getVendorBillingAddress() != null) {
            return po.getVendorBillingAddress();
        }
        Contact vendor = po.getVendor();
        if (vendor == null || vendor.getAddresses() == null || vendor.getAddresses().isEmpty()) {
            return null;
        }
        return vendor.getAddresses().stream()
                .filter(ContactAddress::isDefault)
                .findFirst()
                .orElse(vendor.getAddresses().get(0));
    }

    /** Who the order is addressed to: the vendor's primary contact person, or the first one on file. */
    private ContactPersonDetail vendorPerson(Contact vendor) {
        if (vendor == null || vendor.getPersonDetails() == null || vendor.getPersonDetails().isEmpty()) {
            return null;
        }
        return vendor.getPersonDetails().stream()
                .filter(ContactPersonDetail::isPrimary)
                .findFirst()
                .orElse(vendor.getPersonDetails().get(0));
    }

    public byte[] generate(PurchaseOrder po) {
        Context ctx = new Context();
        ctx.setVariable("brand", documentBrandingService.current());
        ctx.setVariable("po", po);
        ctx.setVariable("company", companyRepo.findAll().stream().findFirst().orElse(null));
        ctx.setVariable("amountInWords", AmountInWords.convert(po.getGrandTotal()));
        ctx.setVariable("vendorAddress", vendorAddress(po));
        ctx.setVariable("vendorPerson", vendorPerson(po.getVendor()));

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
