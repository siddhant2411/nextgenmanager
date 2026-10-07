package com.nextgenmanager.nextgenmanager.company;

import com.nextgenmanager.nextgenmanager.company.dto.DocumentBrand;
import com.nextgenmanager.nextgenmanager.company.model.BrandImageKind;
import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;
import com.nextgenmanager.nextgenmanager.company.model.CompanyDetails;
import com.nextgenmanager.nextgenmanager.company.model.DocumentBranding;
import com.nextgenmanager.nextgenmanager.company.repository.CompanyDetailsRepository;
import com.nextgenmanager.nextgenmanager.company.repository.DocumentBrandingRepository;
import com.nextgenmanager.nextgenmanager.company.service.BrandImageProcessor;
import com.nextgenmanager.nextgenmanager.company.service.DocumentBrandingService;
import com.nextgenmanager.nextgenmanager.company.service.DocumentBrandingServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What the document tests share: a company, each way of branding it, and a way to read a PDF back. */
public final class BrandingFixtures {

    /** A named branding setup. {@code branding} is null for a company that has never opened the settings. */
    public record Brand(String name, DocumentBranding branding) {

        public BrandingMode mode() {
            return branding == null ? BrandingMode.NONE : branding.getMode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private BrandingFixtures() {
    }

    /** Every mode, with logos and letterheads of the awkward shapes people actually upload. */
    public static List<Brand> brands() {
        return List.of(
                new Brand("no settings saved", null),
                new Brand("text", branding(BrandingMode.NONE, null, null)),
                new Brand("logo, wide", branding(BrandingMode.LOGO, image(1200, 420), null)),
                new Brand("logo, square", branding(BrandingMode.LOGO, image(500, 500), null)),
                new Brand("logo, tall", branding(BrandingMode.LOGO, image(200, 600), null)),
                new Brand("letterhead, full height", branding(BrandingMode.LETTERHEAD, image(500, 500), image(2000, 353))),
                new Brand("letterhead, thin strip", branding(BrandingMode.LETTERHEAD, null, image(2400, 200))),
                new Brand("letterhead, deep", branding(BrandingMode.LETTERHEAD, null, image(1800, 600))),
                new Brand("pre-printed paper", branding(BrandingMode.PREPRINTED, null, null)));
    }

    /** The engine the application configures in ThymeleafConfig. */
    public static TemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    /** A company whose contact line is long enough to wrap, as real ones do. */
    public static CompanyDetails company() {
        CompanyDetails c = new CompanyDetails();
        c.setCompanyName("Vajra Auto Components Private Limited");
        c.setStreet1("Plot No. 214/B, Phase II, G.I.D.C. Industrial Estate");
        c.setStreet2("Opp. Water Tank, Metoda");
        c.setCity("Rajkot");
        c.setState("Gujarat");
        c.setPinCode("360021");
        c.setEmail("accounts@vajraautocomponents.example");
        c.setPhone("+91 98250 00000");
        c.setGstNumber("24ABCDE1234F1Z5");
        c.setPanNumber("ABCDE1234F");
        c.setCinNumber("U29100GJ2012PTC000000");
        c.setBankName("State Bank of India");
        c.setBankAccountNumber("00000012345678901");
        c.setBankIfscCode("SBIN0000000");
        return c;
    }

    public static CompanyDetailsRepository companyRepository() {
        CompanyDetailsRepository companies = mock(CompanyDetailsRepository.class);
        when(companies.findAll()).thenReturn(List.of(company()));
        return companies;
    }

    /** The real branding service over the given stored settings. */
    public static DocumentBrandingService brandingService(DocumentBranding branding) {
        DocumentBrandingRepository brandings = mock(DocumentBrandingRepository.class);
        when(brandings.findAll()).thenReturn(branding == null ? List.of() : List.of(branding));
        return new DocumentBrandingServiceImpl(brandings, companyRepository(), engine());
    }

    public static DocumentBrand brand(DocumentBranding branding) {
        return DocumentBrand.of(branding, company(), null);
    }

    /** Stored settings, with the images put through the same processing an upload gets. */
    public static DocumentBranding branding(BrandingMode mode, byte[] logo, byte[] letterhead) {
        DocumentBranding b = new DocumentBranding();
        b.setMode(mode);
        if (logo != null) {
            BrandImageProcessor.Processed p = BrandImageProcessor.process(BrandImageKind.LOGO, logo);
            b.setLogoImage(p.data());
            b.setLogoContentType(p.contentType());
            b.setLogoWidthPx(p.widthPx());
            b.setLogoHeightPx(p.heightPx());
        }
        if (letterhead != null) {
            BrandImageProcessor.Processed p = BrandImageProcessor.process(BrandImageKind.LETTERHEAD, letterhead);
            b.setLetterheadImage(p.data());
            b.setLetterheadContentType(p.contentType());
            b.setLetterheadWidthPx(p.widthPx());
            b.setLetterheadHeightPx(p.heightPx());
        }
        return b;
    }

    /** A PNG of the given size: a dark field with a light block, so it is visible when rendered. */
    public static byte[] image(int width, int height) {
        return encode(width, height, "png");
    }

    public static byte[] encode(int width, int height, String format) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(0x0F, 0x27, 0x44));
            g.fillRect(0, 0, width, height);
            g.setColor(Color.WHITE);
            g.fillRect(width / 20, height / 4, width / 3, height / 2);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The text of each sheet of the PDF, in order. */
    public static List<String> sheets(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            List<String> sheets = new ArrayList<>();
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                sheets.add(stripper.getText(doc));
            }
            return sheets;
        }
    }

    /** How many images are drawn on the first sheet. */
    public static int imagesOnFirstSheet(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            int images = 0;
            for (var name : doc.getPage(0).getResources().getXObjectNames()) {
                if (doc.getPage(0).getResources().isImageXObject(name)) images++;
            }
            return images;
        }
    }

    public static int count(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) count++;
        return count;
    }

    /**
     * Writes the first sheet as a PNG when the suite is run with {@code -Dbranding.samples.dir=<folder>},
     * for looking at the result. Does nothing otherwise.
     */
    public static void sample(String document, Brand brand, byte[] pdf) throws Exception {
        String dir = System.getProperty("branding.samples.dir");
        if (dir == null || dir.isBlank()) return;
        File folder = new File(dir);
        folder.mkdirs();
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            String name = (document + "--" + brand.name()).replaceAll("[^A-Za-z0-9-]+", "_");
            ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 60), "png", new File(folder, name + ".png"));
        }
    }
}
