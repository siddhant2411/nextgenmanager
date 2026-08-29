package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.model.ContactAddress;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.purchase.dto.*;
import com.nextgenmanager.nextgenmanager.purchase.exception.InvalidPurchaseOrderStateException;
import com.nextgenmanager.nextgenmanager.purchase.exception.PurchaseOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.purchase.mapper.PurchaseOrderMapper;
import com.nextgenmanager.nextgenmanager.purchase.model.*;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderSpecifications;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrder;
import com.nextgenmanager.nextgenmanager.sales.model.SalesOrderItem;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository poRepo;
    private final ContactRepository contactRepo;
    private final InventoryItemRepository itemRepo;
    private final SalesOrderRepository salesOrderRepo;
    private final EntityManager em;
    private final GstResolver gstResolver;
    private final PurchaseOrderTaxCalculator taxCalculator;
    private final PurchaseOrderNumberGenerator numberGen;
    private final PurchaseOrderApprovalService approvalService;
    private final PurchaseOrderPdfService pdfService;
    private final PurchaseOrderMapper mapper;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository poRepo,
                                    ContactRepository contactRepo,
                                    InventoryItemRepository itemRepo,
                                    SalesOrderRepository salesOrderRepo,
                                    EntityManager em,
                                    GstResolver gstResolver,
                                    PurchaseOrderTaxCalculator taxCalculator,
                                    PurchaseOrderNumberGenerator numberGen,
                                    PurchaseOrderApprovalService approvalService,
                                    PurchaseOrderPdfService pdfService,
                                    PurchaseOrderMapper mapper) {
        this.poRepo = poRepo;
        this.contactRepo = contactRepo;
        this.itemRepo = itemRepo;
        this.salesOrderRepo = salesOrderRepo;
        this.em = em;
        this.gstResolver = gstResolver;
        this.taxCalculator = taxCalculator;
        this.numberGen = numberGen;
        this.approvalService = approvalService;
        this.pdfService = pdfService;
        this.mapper = mapper;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    public PurchaseOrderDto create(PurchaseOrderCreateDto dto) {
        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseOrderNumber(numberGen.next());
        applyScalarFields(po, dto.vendorId(), dto.poType(), dto.orderDate(),
                dto.expectedDeliveryDate(), dto.placeOfSupply(), dto.currency(),
                dto.exchangeRate(), dto.paymentTerms(), dto.creditDays(),
                dto.vendorBillingAddressId(), dto.shipToAddressId(), dto.salesOrderId(),
                dto.quotationNumber(), dto.quotationDate(), dto.reference(),
                dto.termsAndConditions(), dto.internalNotes(), dto.remarks());
        po.setItems(buildItems(po, dto.items()));
        applyGstTreatmentAndRecalculate(po, dto.gstTreatment());
        return toDto(poRepo.save(po));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderDto getById(Long id) {
        return toDto(findActive(id));
    }

    /**
     * Every supplied filter narrows the result.
     *
     * <p>This used to be an if/else chain that returned on the first filter it recognised, so
     * {@code ?vendorId=5&status=SENT} quietly answered a different question -- every PO for
     * vendor 5, in any status -- and looked like a correct, merely longer, list. The filters are
     * ANDed into one specification now, and the controller rejects any parameter not on
     * {@link PurchaseOrderFilter#KNOWN_PARAMS}, so a misspelt one is a 400 rather than a silently
     * unfiltered page.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrderListDto> list(PurchaseOrderFilter filter, Pageable pageable) {
        PurchaseOrderFilter f = (filter != null ? filter : new PurchaseOrderFilter()).normalized();
        return poRepo.findAll(PurchaseOrderSpecifications.from(f), pageable).map(mapper::toListDto);
    }

    /**
     * Looks a PO up by the number people actually quote to each other.
     *
     * <p>Everything else here is keyed on the surrogate id, which nobody has when they are
     * reading a number off a printed PO, an email or a source spreadsheet. Without this, finding
     * one known PO meant paging the whole table and filtering client-side.
     */
    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderDto getByNumber(String purchaseOrderNumber) {
        return toDto(poRepo.findByPurchaseOrderNumberAndDeletedDateIsNull(purchaseOrderNumber)
                .orElseThrow(() -> new PurchaseOrderNotFoundException(purchaseOrderNumber)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderListDto> getPendingReceipt() {
        return poRepo.findByStatusInAndDeletedDateIsNull(
                List.of(PurchaseOrderStatus.SENT, PurchaseOrderStatus.PARTIALLY_RECEIVED))
                .stream()
                .map(po -> enrichWithDaysOverdue(mapper.toListDto(po), po.getExpectedDeliveryDate()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderListDto> getOverduePOs() {
        Date now = new Date();
        return poRepo.findByStatusInAndExpectedDeliveryDateBeforeAndDeletedDateIsNull(
                List.of(PurchaseOrderStatus.SENT, PurchaseOrderStatus.PARTIALLY_RECEIVED), now)
                .stream()
                .map(po -> enrichWithDaysOverdue(mapper.toListDto(po), po.getExpectedDeliveryDate()))
                .toList();
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    public PurchaseOrderDto update(Long id, PurchaseOrderUpdateDto dto) {
        PurchaseOrder po = findActive(id);
        assertDraft(po);
        applyScalarFields(po, dto.vendorId(), dto.poType(), dto.orderDate(),
                dto.expectedDeliveryDate(), dto.placeOfSupply(), dto.currency(),
                dto.exchangeRate(), dto.paymentTerms(), dto.creditDays(),
                dto.vendorBillingAddressId(), dto.shipToAddressId(), dto.salesOrderId(),
                dto.quotationNumber(), dto.quotationDate(), dto.reference(),
                dto.termsAndConditions(), dto.internalNotes(), dto.remarks());
        if (dto.items() != null) {
            po.getItems().clear();
            po.getItems().addAll(buildItems(po, dto.items()));
        }
        applyGstTreatmentAndRecalculate(po, dto.gstTreatment());
        return toDto(poRepo.save(po));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    public void delete(Long id) {
        PurchaseOrder po = findActive(id);
        assertDraft(po);
        po.setDeletedDate(new Date());
        poRepo.save(po);
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @Override
    public PurchaseOrderDto submit(Long id) {
        PurchaseOrder po = findActive(id);
        if (po.getApprovalStatus() != PurchaseOrderApprovalStatus.DRAFT &&
                po.getApprovalStatus() != PurchaseOrderApprovalStatus.REJECTED) {
            throw new InvalidPurchaseOrderStateException(
                    "Only DRAFT or REJECTED POs can be submitted for approval.");
        }
        po.setApprovalStatus(PurchaseOrderApprovalStatus.PENDING_APPROVAL);
        return toDto(poRepo.save(po));
    }

    @Override
    public PurchaseOrderDto approve(Long id) {
        PurchaseOrder po = findActive(id);
        approvalService.approve(po);
        po.setStatus(PurchaseOrderStatus.DRAFT);
        return toDto(poRepo.save(po));
    }

    @Override
    public PurchaseOrderDto reject(Long id, PurchaseOrderApprovalActionDto dto) {
        PurchaseOrder po = findActive(id);
        approvalService.reject(po, dto.reason());
        return toDto(poRepo.save(po));
    }

    @Override
    public PurchaseOrderDto send(Long id) {
        PurchaseOrder po = findActive(id);
        if (po.getApprovalStatus() != PurchaseOrderApprovalStatus.APPROVED) {
            throw new InvalidPurchaseOrderStateException(
                    "PO must be APPROVED before it can be sent to vendor.");
        }
        po.setStatus(PurchaseOrderStatus.SENT);
        return toDto(poRepo.save(po));
    }

    @Override
    public PurchaseOrderDto cancel(Long id, PurchaseOrderApprovalActionDto dto) {
        PurchaseOrder po = findActive(id);
        if (po.getStatus() == PurchaseOrderStatus.COMPLETED ||
                po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new InvalidPurchaseOrderStateException(
                    "Cannot cancel a COMPLETED or already CANCELLED PO.");
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        po.setInternalNotes(StringUtils.hasText(dto.reason())
                ? "Cancelled: " + dto.reason() : po.getInternalNotes());
        return toDto(poRepo.save(po));
    }

    @Override
    public PurchaseOrderDto complete(Long id) {
        PurchaseOrder po = findActive(id);
        if (po.getStatus() != PurchaseOrderStatus.RECEIVED
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new InvalidPurchaseOrderStateException(
                    "PO must be RECEIVED (or PARTIALLY_RECEIVED) before it can be completed.");
        }
        po.setStatus(PurchaseOrderStatus.COMPLETED);
        return toDto(poRepo.save(po));
    }

    @Override
    public PurchaseOrderDto recalculate(Long id) {
        PurchaseOrder po = findActive(id);
        // Keeps whatever treatment the PO already carries. Re-deriving it here would silently
        // undo an explicit override the first time anything triggered a recalculation.
        applyGstTreatmentAndRecalculate(po, po.getGstTreatment());
        return toDto(poRepo.save(po));
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public String nextNumber() {
        return numberGen.preview();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        return pdfService.generate(findActive(id));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PurchaseOrder findActive(Long id) {
        return poRepo.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new PurchaseOrderNotFoundException(id));
    }

    private void assertDraft(PurchaseOrder po) {
        if (po.getApprovalStatus() != PurchaseOrderApprovalStatus.DRAFT) {
            throw new InvalidPurchaseOrderStateException(
                    "PO " + po.getPurchaseOrderNumber() + " is not in DRAFT state and cannot be edited.");
        }
    }

    private void applyScalarFields(PurchaseOrder po,
                                   Integer vendorId, PurchaseOrderType poType,
                                   Date orderDate, Date expectedDeliveryDate,
                                   String placeOfSupply, String currency,
                                   BigDecimal exchangeRate, String paymentTerms,
                                   Integer creditDays, Integer vendorBillingAddressId,
                                   Integer shipToAddressId, Long salesOrderId,
                                   String quotationNumber, Date quotationDate, String reference,
                                   String termsAndConditions, String internalNotes,
                                   String remarks) {
        if (vendorId != null) {
            Contact vendor = contactRepo.findById(vendorId)
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));
            po.setVendor(vendor);
            // Snapshot payment terms from vendor if not overridden
            if (!StringUtils.hasText(paymentTerms)) {
                po.setPaymentTerms(vendor.getDefaultPaymentTerms());
                po.setCreditDays(vendor.getCreditDays());
            }
        }
        if (poType != null)              po.setPoType(poType);
        if (orderDate != null)           po.setOrderDate(orderDate);
        if (expectedDeliveryDate != null) po.setExpectedDeliveryDate(expectedDeliveryDate);
        if (StringUtils.hasText(placeOfSupply)) po.setPlaceOfSupply(placeOfSupply);
        if (StringUtils.hasText(currency))      po.setCurrency(currency);
        if (exchangeRate != null)        po.setExchangeRate(exchangeRate);
        if (StringUtils.hasText(paymentTerms))  po.setPaymentTerms(paymentTerms);
        if (creditDays != null)          po.setCreditDays(creditDays);
        if (vendorBillingAddressId != null)
            po.setVendorBillingAddress(em.getReference(ContactAddress.class, vendorBillingAddressId));
        if (shipToAddressId != null)
            po.setShipToAddress(em.getReference(ContactAddress.class, shipToAddressId));
        if (salesOrderId != null)
            po.setSalesOrder(em.getReference(SalesOrder.class, salesOrderId));
        if (quotationNumber != null) po.setQuotationNumber(quotationNumber.isBlank() ? null : quotationNumber);
        if (quotationDate != null)   po.setQuotationDate(quotationDate);
        if (reference != null)       po.setReference(reference.isBlank() ? null : reference);
        if (termsAndConditions != null) po.setTermsAndConditions(termsAndConditions);
        if (internalNotes != null)      po.setInternalNotes(internalNotes);
        if (remarks != null)            po.setRemarks(remarks);
    }

    private List<PurchaseOrderItem> buildItems(PurchaseOrder po,
                                               List<PurchaseOrderItemCreateDto> dtos) {
        if (dtos == null) return new ArrayList<>();
        List<PurchaseOrderItem> lines = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseOrderItemCreateDto d : dtos) {
            // Named line, named field. The old message for a missing itemId read
            // "Item not found: null", which sends the caller hunting for a deleted item rather
            // than looking at the line it never filled in.
            final int at = lineNo;
            if (d.itemId() == null) {
                throw new IllegalArgumentException(
                        "Line " + at + " has no itemId. Every purchase order line must reference an "
                        + "existing inventory item -- create the item first, then send its id.");
            }
            InventoryItem invItem = itemRepo.findById(d.itemId().intValue())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Line " + at + " references item id " + d.itemId() + ", which does not exist."));

            PurchaseOrderItem line = new PurchaseOrderItem();
            line.setPurchaseOrder(po);
            line.setLineNumber(d.lineNumber() != null ? d.lineNumber() : lineNo);
            line.setItem(invItem);
            line.setDescription(StringUtils.hasText(d.description()) ? d.description() : invItem.getName());
            line.setHsnCode(invItem.getHsnCode());
            line.setUom(invItem.getUom() != null ? invItem.getUom().name() : null);
            line.setQuantityOrdered(d.quantityOrdered());
            line.setQuantityReceived(0);
            line.setUnitPrice(d.unitPrice() != null ? d.unitPrice() : BigDecimal.ZERO);
            line.setDiscountPct(d.discountPct() != null ? d.discountPct() : BigDecimal.ZERO);

            // Snapshot GST rate: use dto value if provided, else fall back to item finance settings
            BigDecimal gstRate = d.gstRatePct();
            if (gstRate == null && invItem.getProductFinanceSettings() != null
                    && invItem.getProductFinanceSettings().getGstRate() != null) {
                gstRate = BigDecimal.valueOf(invItem.getProductFinanceSettings().getGstRate());
            }
            line.setGstRatePct(gstRate != null ? gstRate : BigDecimal.ZERO);

            if (d.salesOrderItemId() != null)
                line.setSalesOrderItem(em.getReference(SalesOrderItem.class, d.salesOrderItemId()));

            line.setRequiredByDate(d.requiredByDate());
            line.setRemarks(d.remarks());
            lines.add(line);
            lineNo++;
        }
        return lines;
    }

    /**
     * Sets the GST treatment -- preferring an explicit one over the derived default -- then
     * reprices the PO, because the treatment is what decides CGST/SGST vs IGST vs no tax at all.
     *
     * <p>Derivation reads the vendor's GSTIN, and a vendor with no GSTIN on file comes back
     * UNREGISTERED, which the tax calculator prices at zero tax. That is the right answer for a
     * genuinely unregistered vendor and the wrong one for a registered vendor whose GSTIN nobody
     * has typed in yet -- and from here the two are indistinguishable. Callers that do know which
     * is which say so, and the value they send is stored for audit exactly as a derived one is.
     */
    private void applyGstTreatmentAndRecalculate(PurchaseOrder po, GstTreatment explicit) {
        if (explicit != null) {
            po.setGstTreatment(explicit);
        } else if (po.getVendor() != null) {
            po.setGstTreatment(gstResolver.deriveGstTreatment(po.getVendor()));
        }
        // placeOfSupply falls back to the company's own state when the caller did not name one.
        if (!StringUtils.hasText(po.getPlaceOfSupply())) {
            String companyState = gstResolver.resolveCompanyStateCode();
            if (companyState != null) po.setPlaceOfSupply(companyState);
        }
        taxCalculator.recalculate(po);
    }

    @Override
    public PurchaseOrderDto markEmailSent(Long id, String toEmail) {
        PurchaseOrder po = findActive(id);
        po.setSentToVendorAt(new Date());
        po.setSentToVendorEmail(toEmail);
        return toDto(poRepo.save(po));
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseAnalyticsDto getPurchaseAnalytics() {
        // Spend by vendor — top 10
        LinkedHashMap<String, BigDecimal> spendByVendor = new LinkedHashMap<>();
        poRepo.findSpendByVendor(PageRequest.of(0, 10)).forEach(row ->
                spendByVendor.put((String) row[0], (BigDecimal) row[1]));

        // Status counts
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        poRepo.countByStatus().forEach(row ->
                statusCounts.put(row[0].toString(), (Long) row[1]));

        // Overdue POs
        List<PurchaseOrderListDto> overduePOs = getOverduePOs();

        // Monthly spend
        List<PurchaseAnalyticsDto.MonthlySpendDto> monthlySpend = poRepo.findMonthlySpend().stream()
                .map(row -> {
                    int year  = ((Number) row[0]).intValue();
                    int month = ((Number) row[1]).intValue();
                    BigDecimal total = (BigDecimal) row[2];
                    return new PurchaseAnalyticsDto.MonthlySpendDto(
                            String.format("%04d-%02d", year, month), total);
                })
                .toList();

        return new PurchaseAnalyticsDto(spendByVendor, statusCounts, overduePOs, monthlySpend);
    }

    private PurchaseOrderListDto enrichWithDaysOverdue(PurchaseOrderListDto dto, Date expectedDeliveryDate) {
        if (expectedDeliveryDate == null) return dto;
        long diffMs = new Date().getTime() - expectedDeliveryDate.getTime();
        int days = (int) (diffMs / 86_400_000L);
        if (days <= 0) return dto;
        return new PurchaseOrderListDto(
                dto.id(), dto.purchaseOrderNumber(), dto.poType(),
                dto.vendorId(), dto.vendorName(), dto.orderDate(), dto.expectedDeliveryDate(),
                dto.status(), dto.approvalStatus(), dto.currency(), dto.grandTotal(),
                dto.reference(), dto.itemCount(), dto.createdDate(), days);
    }

    private PurchaseOrderDto toDto(PurchaseOrder po) {
        PurchaseOrderDto dto = mapper.toDto(po);
        return new PurchaseOrderDto(
                dto.id(), dto.purchaseOrderNumber(), dto.poType(),
                dto.vendorId(), dto.vendorName(), dto.vendorGstin(),
                dto.vendorEmail(), dto.vendorPhone(),
                dto.vendorBillingAddressId(), dto.shipToAddressId(),
                dto.orderDate(), dto.expectedDeliveryDate(),
                dto.status(), dto.approvalStatus(),
                dto.approvedBy(), dto.approvedDate(), dto.rejectionReason(),
                dto.gstTreatment(), dto.placeOfSupply(),
                dto.currency(), dto.exchangeRate(), dto.paymentTerms(), dto.creditDays(),
                dto.subtotal(), dto.totalDiscount(), dto.taxableValue(),
                dto.cgstAmount(), dto.sgstAmount(), dto.igstAmount(),
                dto.cessAmount(), dto.roundOff(), dto.grandTotal(),
                AmountInWords.convert(po.getGrandTotal()),
                po.getQuotationNumber(), po.getQuotationDate(), po.getReference(),
                po.getSentToVendorAt(), po.getSentToVendorEmail(),
                dto.revisionNo(), dto.parentPoId(), dto.salesOrderId(),
                dto.termsAndConditions(), dto.internalNotes(), dto.remarks(),
                dto.createdDate(), dto.updatedDate(),
                dto.items());
    }
}
