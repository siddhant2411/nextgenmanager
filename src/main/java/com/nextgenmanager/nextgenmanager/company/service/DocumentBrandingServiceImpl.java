package com.nextgenmanager.nextgenmanager.company.service;

import com.nextgenmanager.nextgenmanager.bom.service.BusinessException;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.company.dto.BrandImageDTO;
import com.nextgenmanager.nextgenmanager.company.dto.DocumentBrand;
import com.nextgenmanager.nextgenmanager.company.dto.DocumentBrandingDTO;
import com.nextgenmanager.nextgenmanager.company.model.BrandImageKind;
import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.model.DocumentBranding;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.company.repository.DocumentBrandingRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DocumentBrandingServiceImpl implements DocumentBrandingService {

    private final DocumentBrandingRepository brandingRepository;
    private final CompanyDetailsRepository companyDetailsRepository;
    private final TemplateEngine templateEngine;

    public DocumentBrandingServiceImpl(DocumentBrandingRepository brandingRepository,
                                       CompanyDetailsRepository companyDetailsRepository,
                                       TemplateEngine templateEngine) {
        this.brandingRepository = brandingRepository;
        this.companyDetailsRepository = companyDetailsRepository;
        this.templateEngine = templateEngine;
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentBrand current() {
        return DocumentBrand.of(stored(), company(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentBrandingDTO get() {
        DocumentBranding branding = stored();
        return toDTO(branding != null ? branding : new DocumentBranding());
    }

    @Override
    @Transactional
    public DocumentBrandingDTO updateMode(BrandingMode mode) {
        DocumentBranding branding = storedOrNew();
        if (mode == BrandingMode.LOGO && branding.getLogoImage() == null) {
            throw new BusinessException("Upload a logo before switching documents to the logo header.");
        }
        if (mode == BrandingMode.LETTERHEAD && branding.getLetterheadImage() == null) {
            throw new BusinessException("Upload a letterhead before switching documents to the letterhead header.");
        }
        branding.setMode(mode);
        return toDTO(brandingRepository.save(branding));
    }

    @Override
    @Transactional
    public DocumentBrandingDTO uploadImage(BrandImageKind kind, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose an image to upload.");
        }
        if (file.getSize() > BrandImageProcessor.MAX_UPLOAD_BYTES) {
            throw new BusinessException("The image is larger than 2 MB. Export a smaller PNG or JPEG and try again.");
        }
        byte[] upload;
        try {
            upload = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("The upload could not be read. Try again.");
        }
        BrandImageProcessor.Processed image = BrandImageProcessor.process(kind, upload);

        DocumentBranding branding = storedOrNew();
        if (kind == BrandImageKind.LOGO) {
            branding.setLogoImage(image.data());
            branding.setLogoContentType(image.contentType());
            branding.setLogoWidthPx(image.widthPx());
            branding.setLogoHeightPx(image.heightPx());
        } else {
            branding.setLetterheadImage(image.data());
            branding.setLetterheadContentType(image.contentType());
            branding.setLetterheadWidthPx(image.widthPx());
            branding.setLetterheadHeightPx(image.heightPx());
        }
        return toDTO(brandingRepository.save(branding));
    }

    @Override
    @Transactional
    public DocumentBrandingDTO removeImage(BrandImageKind kind) {
        DocumentBranding branding = storedOrNew();
        if (kind == BrandImageKind.LOGO) {
            branding.setLogoImage(null);
            branding.setLogoContentType(null);
            branding.setLogoWidthPx(null);
            branding.setLogoHeightPx(null);
            if (branding.getMode() == BrandingMode.LOGO) branding.setMode(BrandingMode.NONE);
        } else {
            branding.setLetterheadImage(null);
            branding.setLetterheadContentType(null);
            branding.setLetterheadWidthPx(null);
            branding.setLetterheadHeightPx(null);
            if (branding.getMode() == BrandingMode.LETTERHEAD) branding.setMode(BrandingMode.NONE);
        }
        return toDTO(brandingRepository.save(branding));
    }

    @Override
    @Transactional(readOnly = true)
    public BrandImageDTO getImage(BrandImageKind kind) {
        DocumentBranding branding = stored();
        byte[] data = branding == null ? null
                : kind == BrandImageKind.LOGO ? branding.getLogoImage() : branding.getLetterheadImage();
        if (data == null) {
            throw new ResourceNotFoundException("No " + kind.name().toLowerCase() + " has been uploaded.");
        }
        return new BrandImageDTO(data,
                kind == BrandImageKind.LOGO ? branding.getLogoContentType() : branding.getLetterheadContentType());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] previewPdf(BrandingMode mode) {
        CompanyDetails company = company();
        if (company == null) company = new CompanyDetails();

        String companyAddress = Stream.of(company.getStreet1(), company.getStreet2(), company.getCity(),
                        company.getState(), company.getPinCode())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));

        Context context = new Context();
        context.setVariable("brand", DocumentBrand.of(stored(), company, mode));
        context.setVariable("company", company);
        context.setVariable("companyAddress", companyAddress);

        String html = templateEngine.process("branding_preview", context).replace("&nbsp;", "&#160;");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new RuntimeException("Error generating the branding preview", e);
        }
        return out.toByteArray();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private DocumentBranding stored() {
        return brandingRepository.findAll().stream().findFirst().orElse(null);
    }

    private DocumentBranding storedOrNew() {
        DocumentBranding branding = stored();
        return branding != null ? branding : new DocumentBranding();
    }

    private CompanyDetails company() {
        return companyDetailsRepository.findAll().stream().findFirst().orElse(null);
    }

    private DocumentBrandingDTO toDTO(DocumentBranding e) {
        return new DocumentBrandingDTO(
                e.getMode() != null ? e.getMode() : BrandingMode.NONE,
                e.getLogoImage() != null,
                e.getLogoWidthPx(),
                e.getLogoHeightPx(),
                e.getLetterheadImage() != null,
                e.getLetterheadWidthPx(),
                e.getLetterheadHeightPx(),
                e.getUpdatedDate()
        );
    }
}
