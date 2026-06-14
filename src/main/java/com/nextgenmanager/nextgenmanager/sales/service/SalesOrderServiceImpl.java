package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequestSource;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.sales.events.SalesOrderApprovedEvent;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.model.ReplenishmentStrategy;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationStatus;
import com.nextgenmanager.nextgenmanager.marketing.quotation.repository.QuotationRepository;
import com.nextgenmanager.nextgenmanager.sales.dto.*;
import com.nextgenmanager.nextgenmanager.sales.exception.InvalidSalesOrderStateException;
import com.nextgenmanager.nextgenmanager.sales.exception.SalesOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.sales.mapper.SalesOrderMapper;
import com.nextgenmanager.nextgenmanager.sales.model.*;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderSpecification;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesPaymentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesOrderServiceImpl implements SalesOrderService {

    private static final Logger logger = LoggerFactory.getLogger(SalesOrderServiceImpl.class);

    private final SalesOrderRepository salesOrderRepository;
    private final ContactRepository contactRepository;
    private final QuotationRepository quotationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryInstanceService inventoryInstanceService;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderNumberGenerator orderNumberGenerator;
    private final SalesOrderTaxCalculator taxCalculator;
    private final SalesPaymentRepository salesPaymentRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public SalesOrderDto createSalesOrder(SalesOrderCreateDto dto) {
        logger.info("Creating Sales Order for customerId={}, quotationId={}",
                dto.getCustomerId(), dto.getQuotationId());

        Contact customer = contactRepository.findById(dto.getCustomerId())
                .orElseThrow(() -> new InvalidSalesOrderStateException("Customer not found: " + dto.getCustomerId()));
        Quotation quotation = resolveQuotation(dto.getQuotationId());

        SalesOrder so = new SalesOrder();
        so.setCustomer(customer);
        so.setQuotation(quotation);
        so.setOrderDate(dto.getOrderDate());
        so.setCurrency(dto.getCurrency());
        so.setPaymentTerms(dto.getPaymentTerms());
        so.setIncoterms(dto.getIncoterms());
        so.setVoucherType(VoucherType.SALES_ORDER);
        so.setPoNumber(dto.getPoNumber());
        so.setPoDate(dto.getPoDate());
        so.setStatus(SalesOrderStatus.DRAFT);
        so.setOrderNumber(orderNumberGenerator.next());

        applyHeaderInputs(so, dto);
        applyItems(so, dto.getItems(), /* newOrder */ true);

        taxCalculator.recalculate(so);
        applyLogistics(so, dto);

        SalesOrder saved = salesOrderRepository.save(so);
        logger.info("Sales Order created id={}, orderNumber={}", saved.getId(), saved.getOrderNumber());
        return salesOrderMapper.toDTO(saved);
    }

    @Override
    public SalesOrderDto getSalesOrderById(Long id) {
        SalesOrder salesOrder = salesOrderRepository.findById(id)
                .orElseThrow(() -> new SalesOrderNotFoundException(id));
        SalesOrderDto dto = salesOrderMapper.toDTO(salesOrder);
        populateItemTracking(salesOrder, dto);
        populatePaymentTotals(id, dto);
        return dto;
    }

    private void populatePaymentTotals(Long orderId, SalesOrderDto dto) {
        BigDecimal totalPaid = salesPaymentRepository.sumAmountBySalesOrderId(orderId);
        dto.setTotalAdvancePaid(totalPaid);
        BigDecimal payable = dto.getTotalPayableAmount() != null ? dto.getTotalPayableAmount() : BigDecimal.ZERO;
        dto.setBalanceDue(payable.subtract(totalPaid).max(BigDecimal.ZERO));
    }


    @Override
    public List<SalesOrder> getAllSalesOrders() {
        return salesOrderRepository.findAll();
    }

    @Override
    public SalesOrderDto updateSalesOrder(Long id, SalesOrderCreateDto dto) {
        logger.info("Updating Sales Order id={}", id);

        SalesOrder existing = salesOrderRepository.findById(id)
                .orElseThrow(() -> new SalesOrderNotFoundException(id));

        assertEditable(existing);

        if (dto.getCustomerId() > 0) {
            Contact customer = contactRepository.findById(dto.getCustomerId())
                    .orElseThrow(() -> new InvalidSalesOrderStateException("Customer not found: " + dto.getCustomerId()));
            existing.setCustomer(customer);
        }
        existing.setQuotation(resolveQuotation(dto.getQuotationId()));

        existing.setOrderDate(dto.getOrderDate());
        existing.setCurrency(dto.getCurrency());
        existing.setPaymentTerms(dto.getPaymentTerms());
        existing.setIncoterms(dto.getIncoterms());
        existing.setPoNumber(dto.getPoNumber());
        existing.setPoDate(dto.getPoDate());

        applyHeaderInputs(existing, dto);
        existing.getItems().clear();
        applyItems(existing, dto.getItems(), /* newOrder */ false);

        taxCalculator.recalculate(existing);
        applyLogistics(existing, dto);

        SalesOrder saved = salesOrderRepository.save(existing);
        logger.info("Sales Order updated id={}, orderNumber={}", saved.getId(), saved.getOrderNumber());
        return salesOrderMapper.toDTO(saved);
    }

    @Override
    public void deleteSalesOrder(Long id) {
        SalesOrder existing = salesOrderRepository.findById(id)
                .orElseThrow(() -> new SalesOrderNotFoundException(id));
        assertEditable(existing);
        existing.setDeletedDate(new Date());
        salesOrderRepository.save(existing);
        logger.info("Sales Order id={} marked deleted", id);
    }

    @Override
    @Transactional
    public void salesOrderStatusChange(Long id, SalesOrderStatus newStatus, boolean isInventoryAction) {
        SalesOrder order = salesOrderRepository.findById(id)
                .orElseThrow(() -> new SalesOrderNotFoundException(id));

        SalesOrderStatus current = order.getStatus();
        assertTransitionAllowed(current, newStatus);

        boolean reservedNow = false;
        if (current == SalesOrderStatus.DRAFT && newStatus == SalesOrderStatus.APPROVED && isInventoryAction) {
            reserveInventory(order);
            reservedNow = true;
        }

        order.setStatus(newStatus);
        salesOrderRepository.save(order);
        logger.info("SalesOrder {} status: {} -> {}", id, current, newStatus);

        // Route any MAKE_TO_ORDER shortfalls after commit (same as approve()).
        if (reservedNow) {
            domainEventPublisher.publish(new SalesOrderApprovedEvent(order.getId()));
        }
    }

    @Override
    public SalesOrderDto updateStatus(Long id, SalesOrderStatus status) {
        salesOrderStatusChange(id, status, false);
        return getSalesOrderById(id);
    }

    @Override
    public Page<SalesOrderDisplayDto> getSalesOrderDisplayList(
            int page, int size, String sortBy, String sortDir,
            String orderNumber, LocalDate orderDate, String customerName,
            String poNumber, BigDecimal netAmount, SalesOrderStatus status) {

        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return salesOrderRepository.findAll(
                SalesOrderSpecification.filterOrders(orderNumber, orderDate, customerName, poNumber, netAmount, status),
                pageable
        ).map(this::toDisplayDto);
    }

    // ---------- helpers ----------

    private Quotation resolveQuotation(Long quotationId) {
        if (quotationId == null || quotationId <= 0) return null;
        Quotation q = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new SalesOrderNotFoundException("Quotation not found: " + quotationId));
        
        // Auto-accept if it's not already accepted/completed
        if (q.getQuotationStatus() != QuotationStatus.ACCEPTED) {
            q.setQuotationStatus(QuotationStatus.ACCEPTED);
            quotationRepository.save(q);
            logger.info("Quotation {} auto-accepted via Sales Order link", q.getQtnNo());
        }
        return q;
    }

    private void applyHeaderInputs(SalesOrder so, SalesOrderCreateDto dto) {
        // Tax type MUST be set before items are calculated.
        so.setTaxType(dto.getTaxType() != null ? dto.getTaxType() : TaxType.CGST_SGST);
        so.setTaxPercentage(dto.getTaxPercentage() != null ? dto.getTaxPercentage() : BigDecimal.ZERO);
        so.setDiscountPercentage(dto.getDiscountPercentage() != null ? dto.getDiscountPercentage() : BigDecimal.ZERO);
        so.setIncludeFreightCharges(dto.isIncludeFreightCharges());
        so.setFreightAndForwardingCharges(
                dto.getFreightAndForwardingCharges() != null ? dto.getFreightAndForwardingCharges() : BigDecimal.ZERO);
        so.setPlaceOfSupply(dto.getPlaceOfSupply());
        so.setPlaceOfSupplyStateCode(dto.getPlaceOfSupplyStateCode());
    }

    private void applyItems(SalesOrder so, List<SalesOrderItemDto> itemDtos, boolean newOrder) {
        if (itemDtos == null || itemDtos.isEmpty()) {
            if (newOrder) so.setItems(new ArrayList<>());
            return;
        }
        List<SalesOrderItem> entities = newOrder ? new ArrayList<>() : so.getItems();

        for (SalesOrderItemDto dto : itemDtos) {
            if (dto.getInventoryItem() == null || dto.getInventoryItem().getInventoryItemId() == 0) {
                throw new InvalidSalesOrderStateException("Sales order line is missing inventory item");
            }
            int invItemId = dto.getInventoryItem().getInventoryItemId();
            InventoryItem invItem = inventoryItemRepository.findById(invItemId)
                    .orElseThrow(() -> new InvalidSalesOrderStateException("Inventory item not found: " + invItemId));

            SalesOrderItem item = new SalesOrderItem();
            item.setInventoryItem(invItem);
            item.setHsnCode(dto.getHsnCode() != null ? dto.getHsnCode() : invItem.getHsnCode());
            item.setQty(dto.getQty() != null ? dto.getQty() : BigDecimal.ZERO);
            item.setPricePerUnit(dto.getPricePerUnit() != null ? dto.getPricePerUnit() : BigDecimal.ZERO);
            item.setDiscountPercentage(dto.getDiscountPercentage() != null ? dto.getDiscountPercentage() : BigDecimal.ZERO);

            // Stash full GST rate in igstRate as a transient input; the calculator will
            // redistribute into cgst/sgst (or keep as igst) based on the SO tax type.
            BigDecimal fullGstRate = dto.getGstRatePct() != null ? dto.getGstRatePct() : BigDecimal.ZERO;
            item.setIgstRate(fullGstRate);

            item.setSalesOrder(so);
            entities.add(item);
        }
        if (newOrder) so.setItems(entities);
    }

    private void applyLogistics(SalesOrder so, SalesOrderCreateDto dto) {
        so.setDeliveryAddress(dto.getDeliveryAddress());
        so.setDispatchThrough(dto.getDispatchThrough());
        so.setTransportMode(dto.getTransportMode());
        so.setDeliveryDate(dto.getDeliveryDate());
        so.setPackagingInstructions(dto.getPackagingInstructions());
        so.setRemarks(dto.getRemarks());
        so.setReference(dto.getReference());
    }

    /**
     * Route-aware reservation, run on SO approval.
     *
     * <p>MAKE_TO_ORDER — reserve available stock and raise an order-linked procurement
     * need for the shortfall (routed to a Work Order or Purchase Requisition downstream).
     *
     * <p>MAKE_TO_STOCK — reserve only what is physically on hand; the shortfall is left to
     * reorder rules, not chained to this order. No order-linked procurement need is raised.
     */
    private void reserveInventory(SalesOrder order) {
        String user = currentUsername();
        for (SalesOrderItem item : order.getItems()) {
            if (item.getItemRequestId() != null) continue; // already reserved

            InventoryItem invItem = item.getInventoryItem();
            double orderedQty = item.getQty() != null ? item.getQty().doubleValue() : 0.0;
            if (orderedQty <= 0) continue;

            ProductInventorySettings settings = invItem.getProductInventorySettings();
            ReplenishmentStrategy strategy = (settings != null && settings.getReplenishmentStrategy() != null)
                    ? settings.getReplenishmentStrategy()
                    : ReplenishmentStrategy.MAKE_TO_STOCK;

            if (strategy == ReplenishmentStrategy.MAKE_TO_ORDER) {
                InventoryRequest req = inventoryInstanceService.requestInstance(
                        invItem, orderedQty,
                        InventoryRequestSource.SALES_ORDER, order.getId(),
                        user, "MTO: sales order " + order.getOrderNumber());
                item.setItemRequestId(req.getId());
            } else {
                // MAKE_TO_STOCK: reserve only on-hand qty; reorder rules replenish the rest.
                double available = settings != null ? settings.getAvailableQuantity() : 0.0;
                double toReserve = Math.min(orderedQty, available);
                if (toReserve > 0) {
                    InventoryRequest req = inventoryInstanceService.requestInstance(
                            invItem, toReserve,
                            InventoryRequestSource.SALES_ORDER, order.getId(),
                            user, "MTS: reserve on-hand for sales order " + order.getOrderNumber());
                    item.setItemRequestId(req.getId());
                }
                // else: nothing on hand; leave unreserved for the reorder engine.
            }
        }
    }

    // ---- Approval workflow ----

    @Override
    public SalesOrderDto submit(Long id) {
        SalesOrder so = fetchById(id);
        if (so.getApprovalStatus() != SalesOrderApprovalStatus.DRAFT && so.getApprovalStatus() != SalesOrderApprovalStatus.REJECTED) {
            throw new InvalidSalesOrderStateException(
                    "SO " + so.getOrderNumber() + " must be in DRAFT or REJECTED status to submit. Current: " + so.getApprovalStatus());
        }
        so.setApprovalStatus(SalesOrderApprovalStatus.PENDING_APPROVAL);
        logger.info("SO {} submitted for approval", id);
        return salesOrderMapper.toDTO(salesOrderRepository.save(so));
    }

    @Override
    public SalesOrderDto approve(Long id) {
        SalesOrder so = fetchById(id);
        if (so.getApprovalStatus() != SalesOrderApprovalStatus.PENDING_APPROVAL) {
            throw new InvalidSalesOrderStateException(
                    "SO " + so.getOrderNumber() + " is not pending approval. Current: " + so.getApprovalStatus());
        }
        assertHasRole("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_SALES_ADMIN");

        so.setApprovalStatus(SalesOrderApprovalStatus.APPROVED);
        so.setApprovedBy(currentUsername());
        so.setApprovedDate(new Date());
        so.setRejectionReason(null);
        so.setStatus(SalesOrderStatus.APPROVED);

        reserveInventory(so);
        SalesOrder saved = salesOrderRepository.save(so);
        logger.info("SO {} approved by {}", id, saved.getApprovedBy());

        // Route any MAKE_TO_ORDER shortfalls to WO/PR after this transaction commits.
        domainEventPublisher.publish(new SalesOrderApprovedEvent(saved.getId()));

        return salesOrderMapper.toDTO(saved);
    }

    @Override
    public SalesOrderDto reject(Long id, SalesOrderApprovalActionDto dto) {
        SalesOrder so = fetchById(id);
        if (so.getApprovalStatus() != SalesOrderApprovalStatus.PENDING_APPROVAL) {
            throw new InvalidSalesOrderStateException(
                    "SO " + so.getOrderNumber() + " is not pending approval.");
        }
        assertHasRole("ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_SALES_ADMIN");

        so.setApprovalStatus(SalesOrderApprovalStatus.REJECTED);
        so.setRejectionReason(dto != null ? dto.getReason() : null);
        so.setStatus(SalesOrderStatus.DRAFT);
        logger.info("SO {} rejected", id);
        return salesOrderMapper.toDTO(salesOrderRepository.save(so));
    }

    @Override
    public SalesOrderDto send(Long id, String toEmail) {
        SalesOrder so = fetchById(id);
        if (so.getApprovalStatus() != SalesOrderApprovalStatus.APPROVED) {
            throw new InvalidSalesOrderStateException(
                    "SO " + so.getOrderNumber() + " must be APPROVED before sending.");
        }
        so.setSentToCustomerAt(new Date());
        if (toEmail != null && !toEmail.isBlank()) {
            so.setSentToCustomerEmail(toEmail);
        } else if (so.getCustomer() != null && so.getCustomer().getEmail() != null) {
            so.setSentToCustomerEmail(so.getCustomer().getEmail());
        }
        logger.info("SO {} marked as sent to customer", id);
        return salesOrderMapper.toDTO(salesOrderRepository.save(so));
    }

    @Override
    public SalesOrderDto cancel(Long id, SalesOrderApprovalActionDto dto) {
        SalesOrder so = fetchById(id);
        if (so.getStatus() == SalesOrderStatus.COMPLETED || so.getStatus() == SalesOrderStatus.CANCELLED) {
            throw new InvalidSalesOrderStateException(
                    "SO " + so.getOrderNumber() + " cannot be cancelled in status " + so.getStatus());
        }
        so.setStatus(SalesOrderStatus.CANCELLED);
        so.setRejectionReason(dto != null ? dto.getReason() : null);
        logger.info("SO {} cancelled", id);
        return salesOrderMapper.toDTO(salesOrderRepository.save(so));
    }

    @Override
    public SalesOrderDto complete(Long id) {
        SalesOrder so = fetchById(id);
        if (so.getStatus() != SalesOrderStatus.INVOICED) {
            throw new InvalidSalesOrderStateException(
                    "SO " + so.getOrderNumber() + " must be INVOICED before completing. Current: " + so.getStatus());
        }
        so.setStatus(SalesOrderStatus.COMPLETED);
        logger.info("SO {} completed", id);
        return salesOrderMapper.toDTO(salesOrderRepository.save(so));
    }

    @Override
    public SalesOrderDto recalculate(Long id) {
        SalesOrder so = fetchById(id);
        taxCalculator.recalculate(so);
        return salesOrderMapper.toDTO(salesOrderRepository.save(so));
    }

    @Override
    public String nextNumber() {
        return orderNumberGenerator.preview();
    }

    // ---- Query methods ----

    @Override
    public Map<String, Object> getSalesAnalytics() {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalOrders",      salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.DRAFT)
                                        + salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.APPROVED)
                                        + salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.IN_PROGRESS)
                                        + salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.PARTIALLY_DISPATCHED)
                                        + salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.FULLY_DISPATCHED)
                                        + salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.INVOICED)
                                        + salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.COMPLETED));
        analytics.put("pendingOrders",    salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.DRAFT)
                                        + salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.APPROVED));
        analytics.put("fullyDispatched",  salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.FULLY_DISPATCHED));
        analytics.put("completed",        salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.COMPLETED));
        analytics.put("cancelled",        salesOrderRepository.countByStatusAndNotDeleted(SalesOrderStatus.CANCELLED));

        // Revenue from non-draft, non-cancelled active orders
        BigDecimal totalRevenue = salesOrderRepository
                .findByStatusInAndDeletedDateIsNull(List.of(
                        SalesOrderStatus.APPROVED, SalesOrderStatus.IN_PROGRESS,
                        SalesOrderStatus.PARTIALLY_DISPATCHED, SalesOrderStatus.FULLY_DISPATCHED,
                        SalesOrderStatus.INVOICED, SalesOrderStatus.COMPLETED))
                .stream()
                .map(o -> o.getTotalPayableAmount() != null ? o.getTotalPayableAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        analytics.put("totalRevenue", totalRevenue);
        return analytics;
    }

    @Override
    public List<SalesOrderDisplayDto> getPendingDispatch() {
        return salesOrderRepository.findByStatusInAndDeletedDateIsNull(List.of(
                SalesOrderStatus.APPROVED,
                SalesOrderStatus.IN_PROGRESS,
                SalesOrderStatus.PARTIALLY_DISPATCHED))
                .stream()
                .map(this::toDisplayDto)
                .toList();
    }

    @Override
    public List<SalesOrderDisplayDto> getOverdue() {
        return salesOrderRepository.findOverdue(LocalDate.now())
                .stream()
                .map(this::toDisplayDto)
                .toList();
    }

    @Override
    public SalesOrderProfitabilityDto getProfitability(Long id) {
        SalesOrder so = fetchById(id);

        BigDecimal totalCost = BigDecimal.ZERO;
        Map<Integer, BigDecimal> itemCosts = new HashMap<>();

        // 1. Calculate cost from Delivery Notes (Dispatched)
        if (so.getDeliveryNotes() != null) {
            for (DeliveryNote dn : so.getDeliveryNotes()) {
                if (dn.getItems() != null) {
                    for (DeliveryNoteItem item : dn.getItems()) {
                        BigDecimal dnItemCost = item.getActualCost();
                        
                        // Fallback: If actualCost is null/zero, try to sum from instances or standard cost
                        if (dnItemCost == null || dnItemCost.compareTo(BigDecimal.ZERO) <= 0) {
                            dnItemCost = calculateItemCostFallback(item.getInventoryItem(), (double) item.getQuantityDelivered());
                        }

                        totalCost = totalCost.add(dnItemCost);
                        int invItemId = item.getInventoryItem() != null ? item.getInventoryItem().getInventoryItemId() : -1;
                        itemCosts.put(invItemId, itemCosts.getOrDefault(invItemId, BigDecimal.ZERO).add(dnItemCost));
                    }
                }
            }
        }

        // 2. Estimate cost for remaining quantities (Pending Dispatch)
        if (so.getItems() != null) {
            for (SalesOrderItem soItem : so.getItems()) {
                BigDecimal orderedQty = soItem.getQty() != null ? soItem.getQty() : BigDecimal.ZERO;
                
                // Calculate how much was already accounted for in DNs
                BigDecimal dispatchedQty = BigDecimal.ZERO;
                if (so.getDeliveryNotes() != null) {
                    for (DeliveryNote dn : so.getDeliveryNotes()) {
                        if (dn.getItems() != null) {
                            for (DeliveryNoteItem dni : dn.getItems()) {
                                if (dni.getInventoryItem() != null && dni.getInventoryItem().equals(soItem.getInventoryItem())) {
                                    dispatchedQty = dispatchedQty.add( BigDecimal.valueOf(dni.getQuantityDelivered()));
                                }
                            }
                        }
                    }
                }

                BigDecimal remainingQty = orderedQty.subtract(dispatchedQty);
                if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                    // Estimate cost for the pending portion
                    BigDecimal estimatedPendingCost = calculateItemCostFallback(soItem.getInventoryItem(), remainingQty.doubleValue());
                    totalCost = totalCost.add(estimatedPendingCost);
                    
                    int invItemId = soItem.getInventoryItem() != null ? soItem.getInventoryItem().getInventoryItemId() : -1;
                    itemCosts.put(invItemId, itemCosts.getOrDefault(invItemId, BigDecimal.ZERO).add(estimatedPendingCost));
                }
            }
        }

        BigDecimal revenue = so.getTaxableValue() != null ? so.getTaxableValue() : BigDecimal.ZERO;
        BigDecimal grossProfit = revenue.subtract(totalCost);
        BigDecimal marginPct = BigDecimal.ZERO;
        if (revenue.compareTo(BigDecimal.ZERO) > 0) {
            marginPct = grossProfit.divide(revenue, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        return new SalesOrderProfitabilityDto(
                so.getId(), revenue, totalCost, grossProfit, marginPct, itemCosts
        );
    }

    private BigDecimal calculateItemCostFallback(InventoryItem item, Double qty) {
        if (item == null || qty == null || qty <= 0) return BigDecimal.ZERO;
        
        // Try to get standard cost from finance settings
        BigDecimal unitCost = BigDecimal.ZERO;
        if (item.getProductFinanceSettings() != null && item.getProductFinanceSettings().getStandardCost() != null) {
            unitCost = BigDecimal.valueOf(item.getProductFinanceSettings().getStandardCost());
        }
        
        return unitCost.multiply(BigDecimal.valueOf(qty));
    }


    // ---- private helpers ----

    private SalesOrder fetchById(Long id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new SalesOrderNotFoundException(id));
    }

    private SalesOrderDisplayDto toDisplayDto(SalesOrder order) {
        SalesOrderDisplayDto d = new SalesOrderDisplayDto();
        d.setId(order.getId());
        d.setOrderNumber(order.getOrderNumber());
        d.setOrderDate(order.getOrderDate());
        d.setCustomerName(order.getCustomer() != null ? order.getCustomer().getCompanyName() : null);
        d.setPoNumber(order.getPoNumber());
        d.setNetAmount(order.getNetAmount());
        d.setStatus(order.getStatus());
        d.setApprovalStatus(order.getApprovalStatus());
        d.setDispatchThrough(order.getDispatchThrough());
        d.setDeliveryDate(order.getDeliveryDate());
        d.setUpdatedDate(order.getUpdatedDate());
        d.setCurrency(order.getCurrency());
        d.setPhone(order.getCustomer() != null ? order.getCustomer().getPhone() : null);
        d.setEmail(order.getCustomer() != null ? order.getCustomer().getEmail() : null);
        return d;
    }

    private void assertEditable(SalesOrder so) {
        if (so.getStatus() != SalesOrderStatus.DRAFT) {
            throw new InvalidSalesOrderStateException(
                    "Sales Order " + so.getOrderNumber() + " is not editable in status " + so.getStatus());
        }
    }

    private void assertHasRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new InvalidSalesOrderStateException("Not authenticated");
        boolean ok = auth.getAuthorities().stream()
                .anyMatch(a -> {
                    for (String r : roles) if (a.getAuthority().equals(r)) return true;
                    return false;
                });
        if (!ok) throw new InvalidSalesOrderStateException("Insufficient authority for this action");
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private void populateItemTracking(SalesOrder so, SalesOrderDto dto) {
        if (so == null || dto == null || dto.getItems() == null) return;
        
        for (SalesOrderItemDto itemDto : dto.getItems()) {
            BigDecimal dispatched = BigDecimal.ZERO;
            if (so.getDeliveryNotes() != null) {
                for (DeliveryNote dn : so.getDeliveryNotes()) {
                    if (dn.getItems() != null) {
                        for (DeliveryNoteItem dni : dn.getItems()) {
                            if (dni.getInventoryItem() != null && 
                                dni.getInventoryItem().getInventoryItemId() == itemDto.getInventoryItem().getInventoryItemId()) {
                                dispatched = dispatched.add(BigDecimal.valueOf(dni.getQuantityDelivered()));
                            }
                        }
                    }
                }
            }
            itemDto.setDispatchedQty(dispatched);
            BigDecimal ordered = itemDto.getQty() != null ? itemDto.getQty() : BigDecimal.ZERO;
            itemDto.setRemainingQty(ordered.subtract(dispatched));
        }
    }

    private void assertTransitionAllowed(SalesOrderStatus from, SalesOrderStatus to) {
        if (from == to) {
            throw new InvalidSalesOrderStateException("Sales Order is already in status " + from);
        }
        boolean ok = switch (from) {
            case DRAFT -> to == SalesOrderStatus.APPROVED || to == SalesOrderStatus.CANCELLED;
            case APPROVED -> to == SalesOrderStatus.IN_PROGRESS
                    || to == SalesOrderStatus.PARTIALLY_DISPATCHED
                    || to == SalesOrderStatus.FULLY_DISPATCHED
                    || to == SalesOrderStatus.CANCELLED;
            case IN_PROGRESS -> to == SalesOrderStatus.PARTIALLY_DISPATCHED
                    || to == SalesOrderStatus.FULLY_DISPATCHED
                    || to == SalesOrderStatus.CANCELLED;
            case PARTIALLY_DISPATCHED -> to == SalesOrderStatus.FULLY_DISPATCHED
                    || to == SalesOrderStatus.CANCELLED;
            case FULLY_DISPATCHED -> to == SalesOrderStatus.INVOICED;
            case INVOICED -> to == SalesOrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
        if (!ok) {
            throw new InvalidSalesOrderStateException(
                    "Illegal status transition: " + from + " -> " + to);
        }
    }
}
