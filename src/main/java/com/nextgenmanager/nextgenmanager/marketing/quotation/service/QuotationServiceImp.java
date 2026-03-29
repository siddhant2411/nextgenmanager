package com.nextgenmanager.nextgenmanager.marketing.quotation.service;

import com.itextpdf.html2pdf.HtmlConverter;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.contact.model.ContactPersonDetail;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.quotation.dto.QuotationDisplayDTO;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationProducts;
import com.nextgenmanager.nextgenmanager.marketing.quotation.repository.QuotationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class QuotationServiceImp implements QuotationService {

    @Autowired private QuotationRepository quotationRepository;
    @Autowired private InventoryInstanceRepository inventoryInstanceRepository;

    Logger logger = LoggerFactory.getLogger(QuotationServiceImp.class);

    @Override
    public Quotation getQuotationById(Long id) {
        logger.info("Fetching Quotation with ID: {}", id);

        Quotation quotation = quotationRepository.findByActiveId(id);

        if (quotation == null) {
            logger.error("Quotation not found with ID: {}", id);
            throw new RuntimeException("Quotation not found with ID: " + id);
        }

        logger.info("Quotation fetched successfully with ID: {}", id);
        return quotation;
    }



    @Override
    public List<Quotation> getQuotationList() {
        logger.info("Fetching all quotations");
        return quotationRepository.findAll();
    }

    @Override
    public Page<QuotationDisplayDTO> getQuotationDisplayList(
            int page, int size, String sortBy, String sortDir,
            String qtnNoFilter, LocalDate qtnDateFilter, LocalDate enqDateFilter,
            String enqNoFilter, String companyNameFilter, BigDecimal netAmountFilter,
            BigDecimal totalAmountFilter) {

        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

        Page<Object[]> activeQuotations = quotationRepository.getActiveQuotation(
                pageable, companyNameFilter, qtnNoFilter, qtnDateFilter, enqDateFilter,
                enqNoFilter, netAmountFilter, totalAmountFilter);

        return activeQuotations.map(record -> new QuotationDisplayDTO(
                ((Number) record[0]).longValue(),
                record[1].toString(),
                ((java.sql.Date) record[2]).toLocalDate(),
                record[3].toString(),
                ((java.sql.Date) record[4]).toLocalDate(),
                record[5].toString(),
                (BigDecimal) record[6],
                (BigDecimal) record[7]
        ));
    }

    @Override
    public byte[] generateQuotationPdf(String html) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HtmlConverter.convertToPdf(html, output);
        return output.toByteArray();
    }

    @Override
    public ResponseEntity<byte[]> downloadQuotationPdf(Long id) {
        String html = parseQuotationTemplate(id);
        String qtnNo = getQuotationById(id).getQtnNo();
        byte[] pdf = generateQuotationPdf(html);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Quotation_" + qtnNo + ".pdf");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private String parseQuotationTemplate(Long id) {
        try {
            // Initialize template resolver
            ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
            templateResolver.setTemplateMode(TemplateMode.HTML);
            templateResolver.setSuffix(".html");

            // Fetch quotation object
            Quotation quotation = getQuotationById(id);
            if (quotation == null) {
                throw new IllegalArgumentException("Quotation with ID 253 not found.");
            }

            // Extract data safely with null checks
            String companyName = (quotation.getEnquiry() != null && quotation.getEnquiry().getContact() != null) ?
                    quotation.getEnquiry().getContact().getCompanyName() : "Unknown Company";
            String companyAddress = "";
            if (quotation.getEnquiry() != null) {
                Contact c = quotation.getEnquiry().getContact();
                if (c != null && c.getAddresses() != null && !c.getAddresses().isEmpty()) {
                    ContactAddress addr = c.getAddresses().get(0);
                    companyAddress = java.util.stream.Stream.of(addr.getStreet1(), addr.getStreet2(),
                            addr.getCity(), addr.getState(), addr.getPinCode(), addr.getCountry())
                            .filter(s -> s != null && !s.isBlank())
                            .collect(java.util.stream.Collectors.joining(", "));
                }
            }
            ContactPersonDetail contactInfo = (quotation.getEnquiry() != null && quotation.getEnquiry().getContact() != null &&
                    !quotation.getEnquiry().getContact().getPersonDetails().isEmpty()) ?
                    quotation.getEnquiry().getContact().getPersonDetails().get(0) : null;

            Enquiry enquiryInfo = (quotation.getEnquiry() != null) ? quotation.getEnquiry() : null;
            List<QuotationProducts> quotationProducts = (quotation.getQuotationProducts() != null) ?
                    quotation.getQuotationProducts() : Collections.emptyList();

            // Set context variables
            Context context = new Context();
            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("companyAddress",companyAddress);
            templateVariables.put("companyName", companyName);
            templateVariables.put("contactInfo", contactInfo);
            templateVariables.put("quotationInfo", quotation);
            templateVariables.put("enquiryInfo", enquiryInfo);
            templateVariables.put("quotationProducts", quotationProducts);
            context.setVariables(templateVariables);

            // Initialize template engine
            SpringTemplateEngine templateEngine = new SpringTemplateEngine();
            templateEngine.setTemplateResolver(templateResolver);

            // Process template
            return templateEngine.process("templates/quotation/quotation", context);

        } catch (Exception e) {
            e.printStackTrace();
            return "<h3>Error generating quotation template</h3><p>" + e.getMessage() + "</p>";
        }
    }

    @Transactional
    @Override
    public Quotation createQuotation(Quotation quotation) throws Exception {
        try {

            List<QuotationProducts> quotationProductsList = quotation.getQuotationProducts();
            quotation.setQuotationProducts(new ArrayList<>());

            // 2) Clean up invalid inventoryItem references
            quotationProductsList.forEach(prod -> {
                if (prod.getInventoryItem() != null && prod.getInventoryItem().getInventoryItemId() <= 0) {
                    prod.setInventoryItem(null);
                }
                else {
                    if(Objects.equals(prod.getProductNameRequired(), "")|| prod.getProductNameRequired()==null)
                        prod.setProductNameRequired(prod.getInventoryItem().getName());
                }

            });


            // 4) Persist
            Quotation saved = quotationRepository.save(quotation);

            // 5) Assign a quotation number if not present
            if (saved.getQtnNo() == null) {
                assignQuotationNumber(saved);
            }
            Quotation finalSaved = saved;
            quotationProductsList.forEach(prod -> prod.setQuotation(finalSaved));
            saved.setQuotationProducts(quotationProductsList);
            // 3) Recompute all financial values
            calculateQuotationValues(saved);

            saved = quotationRepository.save(saved);
            logger.info("Quotation created successfully with ID: {}", saved.getId());
            return saved;
        } catch (Exception e) {
            logger.error("Error while creating quotation: {}", e.getMessage(), e);
            throw new Exception("Error while creating Quotation: " + e.getMessage(), e);
        }
    }

    @Transactional
    @Override
    public Quotation updateQuotation(Quotation quotation, Long id) throws Exception {
        // 1) Load existing so Hibernate can merge
        Quotation existing = quotationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Quotation not found with ID: " + id));
        quotation.setId(existing.getId());

        try {
            // 2) Wire up each child
            if (quotation.getQuotationProducts() != null) {
                quotation.getQuotationProducts()
                        .forEach(prod -> prod.setQuotation(quotation));
            }

            // 3) Drop any bogus inventoryItem references
            quotation.getQuotationProducts().forEach(prod -> {
                if (prod.getInventoryItem() != null
                        && prod.getInventoryItem().getInventoryItemId() <= 0) {
                    prod.setInventoryItem(null);
                } else {
                    if(Objects.equals(prod.getProductNameRequired(), "")|| prod.getProductNameRequired()==null)
                        prod.setProductNameRequired(prod.getInventoryItem().getName());
                }
            });

            // 4) Recompute all the totals, taxes, discounts, etc.
            calculateQuotationValues(quotation);

            // 5) Save and then assign QtnNo if missing
            Quotation saved = quotationRepository.save(quotation);
            if (saved.getQtnNo() == null) {
                assignQuotationNumber(saved);
            }

            return saved;
        } catch (Exception e) {
            throw new Exception("Error while updating Quotation: " + e.getMessage(), e);
        }
    }


    @Override
    public void deleteQuotation(Long id) {
        Quotation quotation = quotationRepository.findById(id).orElseThrow(() ->
                new RuntimeException("Quotation not found with ID: " + id));

        quotation.setDeletedDate(new Date());
        quotationRepository.save(quotation);
    }

    private void calculateQuotationValues(Quotation quotation) {
        BigDecimal netAmount = BigDecimal.ZERO;
        for (QuotationProducts p : quotation.getQuotationProducts()) {
            if (p.getInventoryItem() != null && p.getInventoryItem().getInventoryItemId() <= 0) {
                p.setInventoryItem(null);
            }
            BigDecimal discount = Optional.ofNullable(p.getDiscountPercentage()).orElse(BigDecimal.ZERO);
            BigDecimal factor = BigDecimal.ONE.subtract(discount.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            BigDecimal unitPrice = p.getPricePerUnit().multiply(factor);
            BigDecimal total = unitPrice.multiply(p.getQty());

            p.setUnitPriceAfterDiscount(unitPrice);
            p.setTotalAmountOfProduct(total);
            p.setQuotation(quotation);

            netAmount = netAmount.add(total);
        }

        quotation.setNetAmount(netAmount);

        BigDecimal discountAmount = netAmount.multiply(quotation.getDiscountPercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal discountedNet = netAmount.subtract(discountAmount);

        BigDecimal pandfChanrges = BigDecimal.valueOf(quotation.getPackagingAndForwardingChargesPercentage().doubleValue() * 0.01 * discountedNet.doubleValue());
        BigDecimal gstAmount = discountedNet.add(pandfChanrges)
                .multiply(quotation.getGstPercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal total = discountedNet.add(pandfChanrges).add(gstAmount);
        BigDecimal roundedTotal = BigDecimal.valueOf(Math.round(total.doubleValue()));
        BigDecimal roundOff = roundedTotal.subtract(total);

        quotation.setDiscountAmount(discountAmount);
        quotation.setGstAmount(gstAmount);
        quotation.setTotalAmount(roundedTotal);
        quotation.setRoundOff(roundOff);
    }

    private void assignQuotationNumber(Quotation quotation) {
        String qtnNo = LocalDate.now().getYear() + "-" + String.format("%04d", quotation.getId());
        quotation.setQtnNo(qtnNo);
        quotationRepository.save(quotation);
    }

    @Override
    public List<Quotation> getQuotationsByEnquiryId(Long enquiryId) {
        return quotationRepository.findByEnquiryId(enquiryId);
    }
}
