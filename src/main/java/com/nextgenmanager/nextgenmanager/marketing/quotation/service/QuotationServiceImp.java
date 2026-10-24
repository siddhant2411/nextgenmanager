package com.nextgenmanager.nextgenmanager.marketing.quotation.service;

import com.nextgenmanager.nextgenmanager.common.service.PdfPageFit;
import com.nextgenmanager.nextgenmanager.common.service.PdfFonts;
import com.nextgenmanager.nextgenmanager.common.service.ExchangeRates;
import com.nextgenmanager.nextgenmanager.common.model.DocumentCurrency;
import com.nextgenmanager.nextgenmanager.company.dto.CompanyDetailsDTO;
import com.nextgenmanager.nextgenmanager.company.service.DocumentBrandingService;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.bom.service.BusinessException;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.SalesPriceFloorService;
import com.nextgenmanager.nextgenmanager.contact.model.ContactPersonDetail;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryRepository;
import com.nextgenmanager.nextgenmanager.marketing.quotation.dto.QuotationDisplayDTO;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationProducts;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationStatus;
import com.nextgenmanager.nextgenmanager.marketing.quotation.repository.QuotationRepository;
import com.nextgenmanager.nextgenmanager.purchase.service.AmountInWords;
import com.nextgenmanager.nextgenmanager.sales.dto.DocumentParty;
import com.nextgenmanager.nextgenmanager.sales.service.SalesParties;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class QuotationServiceImp implements QuotationService {

    @Autowired private QuotationRepository quotationRepository;
    @Autowired private DocumentBrandingService documentBrandingService;
    @Autowired private InventoryInstanceRepository inventoryInstanceRepository;
    @Autowired private EnquiryRepository enquiryRepository;
    @Autowired private com.nextgenmanager.nextgenmanager.company.service.CompanyDetailsService companyService;
    @Autowired private SalesPriceFloorService priceFloorService;
    @Autowired private TemplateEngine templateEngine;

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

    /** Item rows on a page when everything fits, as on the order acknowledgement. */
    private static final int ITEMS_PER_PAGE = 10;

    /** Below this the page is given up on: something other than the item rows is what does not fit. */
    private static final int MIN_ITEMS_PER_PAGE = 2;

    @Override
    public byte[] generateQuotationPdf(String html) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        PdfFonts.register(builder);
        builder.useFastMode();
        builder.withHtmlContent(html.replace("&nbsp;", "&#160;"), null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the quotation PDF: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadQuotationPdf(Long id) {
        Quotation quotation = getQuotationById(id);
        byte[] pdf = renderFitted(quotation, buildPdfContext(quotation));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Quotation_" + quotation.getQtnNo() + ".pdf");
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Renders with as many item rows per page as actually fit, the way the order acknowledgement does.
     * A long description or a letterhead makes rows taller than the page was planned for; when the
     * PDF comes out with more sheets than pages, a row is taken off every page and it is laid out again.
     */
    private byte[] renderFitted(Quotation quotation, Context ctx) {
        List<QuotationProducts> items = quotation.getQuotationProducts() != null
                ? quotation.getQuotationProducts() : Collections.emptyList();
        for (int rowsPerPage = ITEMS_PER_PAGE; ; rowsPerPage--) {
            List<List<QuotationProducts>> itemPages = paginate(items, rowsPerPage);
            ctx.setVariable("rowsPerPage", rowsPerPage);
            ctx.setVariable("itemPages", itemPages);
            byte[] pdf = generateQuotationPdf(templateEngine.process("quotation/quotation", ctx));
            if (rowsPerPage <= MIN_ITEMS_PER_PAGE || PdfPageFit.pageCount(pdf) <= itemPages.size()) {
                return pdf;
            }
        }
    }

    /** Pages of exactly {@code rowsPerPage} rows; the last is padded with nulls, printed as blank rows. */
    private static List<List<QuotationProducts>> paginate(List<QuotationProducts> items, int rowsPerPage) {
        List<List<QuotationProducts>> pages = new ArrayList<>();
        int pageCount = Math.max(1, (int) Math.ceil((double) items.size() / rowsPerPage));
        for (int p = 0; p < pageCount; p++) {
            List<QuotationProducts> page = new ArrayList<>();
            for (int i = 0; i < rowsPerPage; i++) {
                int idx = p * rowsPerPage + i;
                page.add(idx < items.size() ? items.get(idx) : null);
            }
            pages.add(page);
        }
        return pages;
    }

    private Context buildPdfContext(Quotation quotation) {
        Enquiry enquiry = quotation.getEnquiry();
        Contact customer = enquiry != null ? enquiry.getContact() : null;
        ContactPersonDetail contactPerson = kindAttention(enquiry, customer);

        CompanyDetailsDTO company = companyService.get();
        String companyAddress = java.util.stream.Stream.of(company.street1(), company.street2(), company.city())
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        if (company.pinCode() != null && !company.pinCode().isBlank())
            companyAddress += (companyAddress.isEmpty() ? "" : " - ") + company.pinCode();
        if (company.state() != null && !company.state().isBlank())
            companyAddress += (companyAddress.isEmpty() ? "" : ", ") + company.state();

        BigDecimal subTotal = orZero(quotation.getNetAmount());
        BigDecimal discount = orZero(quotation.getDiscountAmount());
        BigDecimal packing = orZero(quotation.getPackagingAndForwardingCharges());
        BigDecimal total = orZero(quotation.getTotalAmount());

        Integer rev = quotation.getRevisionNumber();
        String qtnNoLabel = quotation.getQtnNo() + (rev != null && rev > 0 ? " (Rev " + rev + ")" : "");

        Context ctx = new Context();
        ctx.setVariable("brand", documentBrandingService.current());
        ctx.setVariable("company", company);
        ctx.setVariable("companyAddress", companyAddress);
        ctx.setVariable("quotation", quotation);
        ctx.setVariable("qtnNoLabel", qtnNoLabel);
        ctx.setVariable("enquiry", enquiry);
        DocumentParty to = SalesParties.of(customer);
        if (customer == null && enquiry != null && enquiry.getManualCompanyName() != null
                && !enquiry.getManualCompanyName().isBlank()) {
            // An enquiry from someone not yet in the contact master still names the company it is for.
            to = new DocumentParty(enquiry.getManualCompanyName().trim(), null, null, null);
        }
        ctx.setVariable("customer", to);
        ctx.setVariable("contactPerson", contactPerson);
        ctx.setVariable("subTotal", subTotal);
        ctx.setVariable("discountAmount", discount);
        ctx.setVariable("packingAmount", packing);
        ctx.setVariable("taxableValue", subTotal.subtract(discount).add(packing));
        ctx.setVariable("gstAmount", orZero(quotation.getGstAmount()));
        ctx.setVariable("roundOff", orZero(quotation.getRoundOff()));
        ctx.setVariable("totalAmount", total);
        ctx.setVariable("cur", DocumentCurrency.signOf(quotation.getCurrency()));
        ctx.setVariable("amountInWords", AmountInWords.convert(total, quotation.getCurrency()));
        return ctx;
    }

    /**
     * The person the quotation is addressed to: whoever the enquiry names, else the customer's
     * primary contact person, else their first one. Null when there is nobody to name.
     */
    static ContactPersonDetail kindAttention(Enquiry enquiry, Contact customer) {
        if (enquiry != null && enquiry.getContactPersonName() != null && !enquiry.getContactPersonName().isBlank()) {
            ContactPersonDetail fromEnquiry = new ContactPersonDetail();
            fromEnquiry.setPersonName(enquiry.getContactPersonName().trim());
            fromEnquiry.setPhoneNumber(enquiry.getContactPersonPhone());
            fromEnquiry.setEmailId(enquiry.getContactPersonEmail());
            return fromEnquiry;
        }
        if (customer == null || customer.getPersonDetails() == null) return null;
        List<ContactPersonDetail> people = customer.getPersonDetails().stream()
                .filter(p -> p.getPersonName() != null && !p.getPersonName().isBlank())
                .toList();
        return people.stream().filter(ContactPersonDetail::isPrimary).findFirst()
                .orElse(people.isEmpty() ? null : people.get(0));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Transactional
    @Override
    public Quotation createQuotation(Quotation quotation) throws Exception {
        quotation.setExchangeRate(ExchangeRates.normalise(quotation.getCurrency(), quotation.getExchangeRate()));
        enforcePriceFloor(quotation, null);
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
        quotation.setExchangeRate(ExchangeRates.normalise(quotation.getCurrency(), quotation.getExchangeRate()));
        enforcePriceFloor(quotation, existing);

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

    /**
     * A sales user may not price a line below the item's minimum selling price; a sales admin may.
     * A line already stored below the minimum is left alone unless this save lowers it further, so
     * a price a sales admin agreed does not block every later edit of the quotation.
     */
    private void enforcePriceFloor(Quotation incoming, Quotation existing) {
        if (incoming.getQuotationProducts() == null || priceFloorService.callerMayGoBelowFloor()) {
            return;
        }
        Map<Long, QuotationProducts> stored = new HashMap<>();
        if (existing != null && existing.getQuotationProducts() != null) {
            existing.getQuotationProducts().forEach(p -> stored.put(p.getId(), p));
        }

        List<String> belowFloor = new ArrayList<>();
        for (QuotationProducts line : incoming.getQuotationProducts()) {
            InventoryItem item = line.getInventoryItem();
            BigDecimal floor = priceFloorService.floorFor(item);
            // The minimum is in rupees, so a foreign-currency price is compared at the quotation's rate.
            BigDecimal offered = ExchangeRates.toBase(SalesPriceFloorService.effectiveUnitPrice(
                    line.getPricePerUnit(), line.getDiscountPercentage(), incoming.getDiscountPercentage()),
                    incoming.getExchangeRate());
            if (!SalesPriceFloorService.isBelow(offered, floor)) continue;

            QuotationProducts before = line.getId() != null ? stored.get(line.getId()) : null;
            if (before != null && before.getInventoryItem() != null
                    && before.getInventoryItem().getInventoryItemId() == item.getInventoryItemId()) {
                BigDecimal agreed = ExchangeRates.toBase(SalesPriceFloorService.effectiveUnitPrice(
                        before.getPricePerUnit(), before.getDiscountPercentage(), existing.getDiscountPercentage()),
                        existing.getExchangeRate());
                if (offered.compareTo(agreed) >= 0) continue;
            }

            String name = line.getProductNameRequired() != null && !line.getProductNameRequired().isBlank()
                    ? line.getProductNameRequired() : "Item " + item.getInventoryItemId();
            belowFloor.add(name + " at Rs. " + offered.toPlainString() + " (minimum Rs. " + floor.toPlainString() + ")");
        }
        if (!belowFloor.isEmpty()) {
            throw new BusinessException("Price is below the minimum selling price after discounts: "
                    + String.join("; ", belowFloor) + ". Only a sales admin can quote below the minimum.");
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
        // A rupee quotation is rounded to the rupee; one in another currency only to its cent.
        BigDecimal roundedTotal = ExchangeRates.isBase(quotation.getCurrency())
                ? BigDecimal.valueOf(Math.round(total.doubleValue()))
                : total.setScale(2, RoundingMode.HALF_UP);
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
        revised.setCurrency(existing.getCurrency());
        revised.setExchangeRate(existing.getExchangeRate());
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
