package com.nextgenmanager.nextgenmanager.marketing.quotation.service;

import com.itextpdf.html2pdf.HtmlConverter;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.contact.model.ContactPersonDetail;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryRepository;
import com.nextgenmanager.nextgenmanager.marketing.quotation.dto.QuotationDisplayDTO;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationProducts;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationStatus;
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
    @Autowired private EnquiryRepository enquiryRepository;
    @Autowired private com.nextgenmanager.nextgenmanager.company.service.CompanyDetailsService companyService;

    Logger logger = LoggerFactory.getLogger(QuotationServiceImp.class);

    @Override
    @Transactional(readOnly = true)
    public Quotation getQuotationById(Long id) {
        logger.info("Fetching Quotation with ID: {}", id);

        Quotation quotation = quotationRepository.findByActiveId(id);

        if (quotation == null) {
            logger.error("Quotation not found with ID: {}", id);
            throw new RuntimeException("Quotation not found with ID: " + id);
        }

        initializeLazyAssociations(quotation);
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
                record[1] != null ? record[1].toString() : null,
                record[2] != null ? ((java.sql.Date) record[2]).toLocalDate() : null,
                record[3] != null ? record[3].toString() : null,
                record[4] != null ? ((java.sql.Date) record[4]).toLocalDate() : null,
                record[5] != null ? record[5].toString() : null,
                (BigDecimal) record[6],
                (BigDecimal) record[7],
                record[8] != null ? record[8].toString() : "DRAFT",
                record[9] != null ? record[9].toString() : "INR",
                record[10] != null ? record[10].toString() : null,
                record[11] != null ? record[11].toString() : null
        ));
    }

    @Override
    public byte[] generateQuotationPdf(String html) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HtmlConverter.convertToPdf(html, output);
        return output.toByteArray();
    }

    @Override
    @Transactional(readOnly = true)
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

            // Fetch company details
            com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsDTO appCompany = companyService.get();

            // Set context variables
            Context context = new Context();
            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("companyAddress",companyAddress);
            templateVariables.put("companyName", companyName);
            templateVariables.put("contactInfo", contactInfo);
            templateVariables.put("quotationInfo", quotation);
            templateVariables.put("enquiryInfo", enquiryInfo);
            templateVariables.put("quotationProducts", quotationProducts);
            templateVariables.put("appCompany", appCompany);
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
            initializeLazyAssociations(saved);
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

            initializeLazyAssociations(saved);
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

    private void initializeLazyAssociations(Quotation quotation) {
        org.hibernate.Hibernate.initialize(quotation.getQuotationProducts());
        if (quotation.getQuotationProducts() != null) {
            quotation.getQuotationProducts().forEach(p -> org.hibernate.Hibernate.initialize(p.getInventoryItem()));
        }
        if (quotation.getEnquiry() != null) {
            org.hibernate.Hibernate.initialize(quotation.getEnquiry());
            org.hibernate.Hibernate.initialize(quotation.getEnquiry().getEnquiredProducts());
            if (quotation.getEnquiry().getEnquiredProducts() != null) {
                quotation.getEnquiry().getEnquiredProducts().forEach(p -> org.hibernate.Hibernate.initialize(p.getInventoryItem()));
            }
            org.hibernate.Hibernate.initialize(quotation.getEnquiry().getEnquiryConversationRecords());
            org.hibernate.Hibernate.initialize(quotation.getEnquiry().getAssignedTo());
            if (quotation.getEnquiry().getContact() != null) {
                org.hibernate.Hibernate.initialize(quotation.getEnquiry().getContact());
                org.hibernate.Hibernate.initialize(quotation.getEnquiry().getContact().getAddresses());
                org.hibernate.Hibernate.initialize(quotation.getEnquiry().getContact().getPersonDetails());
            }
        }
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

    @Transactional
    @Override
    public Quotation reviseQuotation(Long id) throws Exception {
        Quotation existing = getQuotationById(id);
        
        Quotation revised = new Quotation();
        // Copy fields
        revised.setQtnDate(LocalDate.now());
        revised.setEnquiry(existing.getEnquiry());
        revised.setNetAmount(existing.getNetAmount());
        revised.setPackagingAndForwardingCharges(existing.getPackagingAndForwardingCharges());
        revised.setPackagingAndForwardingChargesPercentage(existing.getPackagingAndForwardingChargesPercentage());
        revised.setGstPercentage(existing.getGstPercentage());
        revised.setDiscountPercentage(existing.getDiscountPercentage());
        revised.setGstAmount(existing.getGstAmount());
        revised.setDiscountAmount(existing.getDiscountAmount());
        revised.setRoundOff(existing.getRoundOff());
        revised.setTotalAmount(existing.getTotalAmount());
        revised.setQuotationStatus(QuotationStatus.DRAFT);
        revised.setValidTill(existing.getValidTill());
        revised.setPaymentTerms(existing.getPaymentTerms());
        revised.setDeliveryTerms(existing.getDeliveryTerms());
        revised.setInspectionTerms(existing.getInspectionTerms());
        revised.setPricesTerms(existing.getPricesTerms());
        revised.setNotes(existing.getNotes());
        
        // Revision logic
        int newRevision = existing.getRevisionNumber() + 1;
        revised.setRevisionNumber(newRevision);
        revised.setParentQuotationId(existing.getId());
        
        // Base qtnNo logic
        String baseQtnNo = existing.getQtnNo();
        if (baseQtnNo.contains("-R")) {
            baseQtnNo = baseQtnNo.substring(0, baseQtnNo.lastIndexOf("-R"));
        }
        revised.setQtnNo(baseQtnNo + "-R" + newRevision);
        
        // Products
        List<QuotationProducts> newProducts = new ArrayList<>();
        if (existing.getQuotationProducts() != null) {
            for (QuotationProducts ep : existing.getQuotationProducts()) {
                QuotationProducts np = new QuotationProducts();
                np.setQuotation(revised);
                np.setInventoryItem(ep.getInventoryItem());
                np.setQty(ep.getQty());
                np.setPricePerUnit(ep.getPricePerUnit());
                np.setDiscountPercentage(ep.getDiscountPercentage());
                np.setUnitPriceAfterDiscount(ep.getUnitPriceAfterDiscount());
                np.setTotalAmountOfProduct(ep.getTotalAmountOfProduct());
                np.setSpecialInstruction(ep.getSpecialInstruction());
                np.setProductNameRequired(ep.getProductNameRequired());
                newProducts.add(np);
            }
        }
        revised.setQuotationProducts(newProducts);
        
        // Mark old as REVISED
        existing.setQuotationStatus(QuotationStatus.REVISED);
        quotationRepository.save(existing);
        syncEnquiryStatus(existing);
        
        return quotationRepository.save(revised);
    }

    @Transactional
    @Override
    public Quotation updateQuotationStatus(Long id, String statusStr) throws Exception {
        Quotation quotation = getQuotationById(id);
        QuotationStatus status = QuotationStatus.valueOf(statusStr.toUpperCase());
        quotation.setQuotationStatus(status);
        quotation = quotationRepository.save(quotation);
        
        syncEnquiryStatus(quotation);
        
        return quotation;
    }
    
    private void syncEnquiryStatus(Quotation quotation) {
        if (quotation.getEnquiry() == null) return;
        Enquiry enquiry = quotation.getEnquiry();
        
        QuotationStatus qStatus = quotation.getQuotationStatus();
        boolean statusChanged = false;
        
        if (qStatus == QuotationStatus.SENT && enquiry.getStatus() != EnquiryStatus.QUOTED && enquiry.getStatus() != EnquiryStatus.CONVERTED) {
            enquiry.setStatus(EnquiryStatus.QUOTED);
            statusChanged = true;
        } else if (qStatus == QuotationStatus.ACCEPTED && enquiry.getStatus() != EnquiryStatus.CONVERTED) {
            enquiry.setStatus(EnquiryStatus.CONVERTED);
            statusChanged = true;
        } else if (qStatus == QuotationStatus.REVISED && enquiry.getStatus() != EnquiryStatus.NEGOTIATION && enquiry.getStatus() != EnquiryStatus.CONVERTED) {
            enquiry.setStatus(EnquiryStatus.NEGOTIATION);
            statusChanged = true;
        } else if (qStatus == QuotationStatus.REJECTED) {
            // Usually we do not mark enquiry as LOST just because one quotation is rejected, but could map it if needed.
        }
        
        if (statusChanged) {
            enquiryRepository.save(enquiry);
        }
    }
}
