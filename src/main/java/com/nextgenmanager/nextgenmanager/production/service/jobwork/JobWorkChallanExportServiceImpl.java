package com.nextgenmanager.nextgenmanager.production.service.jobwork;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsDTO;
import com.nextgenmanager.nextgenmanager.company.service.CompanyDetailsService;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.production.dto.JobWorkChallanDTO;
import com.nextgenmanager.nextgenmanager.production.model.JobWorkChallan;
import com.nextgenmanager.nextgenmanager.production.repository.JobWorkChallanRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class JobWorkChallanExportServiceImpl implements JobWorkChallanExportService {

    @Autowired private JobWorkChallanRepository challanRepo;
    @Autowired private JobWorkChallanService     challanService;
    @Autowired private CompanyDetailsService     companyDetailsService;

    private final TemplateEngine templateEngine;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd MMM yyyy");

    public JobWorkChallanExportServiceImpl() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    @Override
    public byte[] generateChallanPdf(Long challanId) throws Exception {
        JobWorkChallan entity = challanRepo.findById(challanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challan not found: " + challanId));
        if (entity.getDeletedDate() != null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Challan not found: " + challanId);

        JobWorkChallanDTO dto = challanService.getById(challanId);

        // ── Company details from DB ──────────────────────────────────────────
        CompanyDetailsDTO company = companyDetailsService.get();
        String companyAddress = buildCompanyAddress(company);

        // ── Vendor address ──────────────────────────────────────────────────
        Contact vendor = entity.getVendor();
        String vendorAddress = resolveVendorAddress(vendor);
        String vendorPhone   = vendor.getPhone()  != null ? vendor.getPhone()  : "";
        String vendorEmail   = vendor.getEmail()  != null ? vendor.getEmail()  : "";

        // ── Line values ─────────────────────────────────────────────────────
        List<String> lineValues = dto.getLines().stream().map(l -> {
            if (l.getValuePerUnit() != null && l.getQuantityDispatched() != null) {
                return l.getValuePerUnit()
                        .multiply(l.getQuantityDispatched())
                        .setScale(2, RoundingMode.HALF_UP)
                        .toPlainString();
            }
            return "—";
        }).collect(Collectors.toList());

        BigDecimal totalValue = dto.getLines().stream()
                .filter(l -> l.getValuePerUnit() != null && l.getQuantityDispatched() != null)
                .map(l -> l.getValuePerUnit().multiply(l.getQuantityDispatched()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalQtyDispatched = dto.getLines().stream()
                .filter(l -> l.getQuantityDispatched() != null)
                .map(JobWorkChallanDTO.LineDTO::getQuantityDispatched)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalQtyPending = dto.getLines().stream()
                .filter(l -> l.getQuantityPending() != null)
                .map(JobWorkChallanDTO.LineDTO::getQuantityPending)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // ── Padding rows ────────────────────────────────────────────────────
        int padCount = Math.max(0, 8 - dto.getLines().size());
        List<Integer> paddingRows = new ArrayList<>();
        for (int i = 0; i < padCount; i++) paddingRows.add(i);

        // ── Dates ───────────────────────────────────────────────────────────
        Date ref = dto.getDispatchDate() != null ? dto.getDispatchDate() : dto.getCreationDate();
        String issueDate     = ref != null ? DATE_FMT.format(ref) : DATE_FMT.format(new Date());
        String dispatchDate  = dto.getDispatchDate()       != null ? DATE_FMT.format(dto.getDispatchDate())       : "—";
        String returnByDate  = dto.getExpectedReturnDate() != null ? DATE_FMT.format(dto.getExpectedReturnDate()) : "—";
        String generatedDate = DATE_FMT.format(new Date());

        // ── QR code ─────────────────────────────────────────────────────────
        String qrCode = generateQrCodeBase64(dto.getChallanNumber());

        // ── Thymeleaf context ────────────────────────────────────────────────
        Context context = new Context();
        context.setVariable("challan",            dto);
        context.setVariable("company",            company);
        context.setVariable("companyAddress",     companyAddress);
        context.setVariable("vendorAddress",      vendorAddress);
        context.setVariable("vendorPhone",        vendorPhone);
        context.setVariable("vendorEmail",        vendorEmail);
        context.setVariable("lineValues",         lineValues);
        context.setVariable("totalValue",         totalValue.toPlainString());
        context.setVariable("totalQtyDispatched", totalQtyDispatched.toPlainString());
        context.setVariable("totalQtyPending",    totalQtyPending.toPlainString());
        context.setVariable("paddingRows",        paddingRows);
        context.setVariable("issueDate",          issueDate);
        context.setVariable("dispatchDate",       dispatchDate);
        context.setVariable("returnByDate",       returnByDate);
        context.setVariable("generatedDate",      generatedDate);
        context.setVariable("qrCode",             qrCode);

        String html = templateEngine.process("job_work_challan", context);
        return renderPdf(html);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String buildCompanyAddress(CompanyDetailsDTO c) {
        StringJoiner sj = new StringJoiner(", ");
        if (hasText(c.street1()))  sj.add(c.street1());
        if (hasText(c.street2()))  sj.add(c.street2());
        if (hasText(c.city()))     sj.add(c.city());
        if (hasText(c.state()))    sj.add(c.state());
        if (hasText(c.pinCode()))  sj.add(c.pinCode());
        if (hasText(c.country()))  sj.add(c.country());
        return sj.toString();
    }

    private String resolveVendorAddress(Contact vendor) {
        if (vendor.getAddresses() == null || vendor.getAddresses().isEmpty()) return "";
        ContactAddress addr = vendor.getAddresses().stream()
                .filter(ContactAddress::isDefault)
                .findFirst()
                .orElse(vendor.getAddresses().get(0));
        return formatVendorAddress(addr);
    }

    private String formatVendorAddress(ContactAddress a) {
        StringJoiner sj = new StringJoiner(", ");
        if (hasText(a.getStreet1())) sj.add(a.getStreet1());
        if (hasText(a.getStreet2())) sj.add(a.getStreet2());
        if (hasText(a.getCity()))    sj.add(a.getCity());
        if (hasText(a.getState()))   sj.add(a.getState());
        if (hasText(a.getPinCode())) sj.add(a.getPinCode());
        if (hasText(a.getCountry())) sj.add(a.getCountry());
        return sj.toString();
    }

    private boolean hasText(String s) { return s != null && !s.isBlank(); }

    private byte[] renderPdf(String html) throws Exception {
        html = html.replace("&nbsp;",  "&#160;")
                   .replace("&mdash;", "&#8212;")
                   .replace("&ndash;", "&#8211;")
                   .replace("&bull;",  "&#8226;")
                   .replace("&laquo;", "&#171;")
                   .replace("&raquo;", "&#187;")
                   .replace("&copy;",  "&#169;")
                   .replace("&reg;",   "&#174;")
                   .replace("&trade;", "&#8482;");
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    private String generateQrCodeBase64(String text) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 200, 200);
        try (ByteArrayOutputStream png = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return Base64.getEncoder().encodeToString(png.toByteArray());
        }
    }
}
