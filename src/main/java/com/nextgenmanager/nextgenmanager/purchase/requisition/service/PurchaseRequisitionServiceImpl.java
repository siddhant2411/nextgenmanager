package com.nextgenmanager.nextgenmanager.purchase.requisition.service;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemVendorPrice;
import com.nextgenmanager.nextgenmanager.items.model.PriceType;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ItemVendorPriceRepository;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrder;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderMaterial;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderMaterialRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workorder.WorkOrderRepository;
import com.nextgenmanager.nextgenmanager.production.repository.workcenter.WorkCenterRepository;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.PurchaseOrderItemCreateDto;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderType;
import com.nextgenmanager.nextgenmanager.purchase.service.PurchaseOrderService;
import com.nextgenmanager.nextgenmanager.purchase.requisition.dto.*;
import com.nextgenmanager.nextgenmanager.purchase.requisition.exception.InvalidPurchaseRequisitionStateException;
import com.nextgenmanager.nextgenmanager.purchase.requisition.exception.PurchaseRequisitionNotFoundException;
import com.nextgenmanager.nextgenmanager.purchase.requisition.mapper.PurchaseRequisitionMapper;
import com.nextgenmanager.nextgenmanager.purchase.requisition.model.*;
import com.nextgenmanager.nextgenmanager.purchase.requisition.repository.PurchaseRequisitionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class PurchaseRequisitionServiceImpl implements PurchaseRequisitionService {

    private final PurchaseRequisitionRepository prRepo;
    private final ContactRepository contactRepo;
    private final InventoryItemRepository itemRepo;
    private final WorkOrderRepository workOrderRepo;
    private final WorkOrderMaterialRepository workOrderMaterialRepo;
    private final WorkCenterRepository workCenterRepo;
    private final ItemVendorPriceRepository vendorPriceRepo;
    private final EntityManager em;
    private final PurchaseRequisitionNumberGenerator numberGen;
    private final PurchaseRequisitionApprovalService approvalService;
    private final PurchaseRequisitionMapper mapper;
    private final PurchaseOrderService purchaseOrderService;

    public PurchaseRequisitionServiceImpl(PurchaseRequisitionRepository prRepo,
                                          ContactRepository contactRepo,
                                          InventoryItemRepository itemRepo,
                                          WorkOrderRepository workOrderRepo,
                                          WorkOrderMaterialRepository workOrderMaterialRepo,
                                          WorkCenterRepository workCenterRepo,
                                          ItemVendorPriceRepository vendorPriceRepo,
                                          EntityManager em,
                                          PurchaseRequisitionNumberGenerator numberGen,
                                          PurchaseRequisitionApprovalService approvalService,
                                          PurchaseRequisitionMapper mapper,
                                          PurchaseOrderService purchaseOrderService) {
        this.prRepo = prRepo;
        this.contactRepo = contactRepo;
        this.itemRepo = itemRepo;
        this.workOrderRepo = workOrderRepo;
        this.workOrderMaterialRepo = workOrderMaterialRepo;
        this.workCenterRepo = workCenterRepo;
        this.vendorPriceRepo = vendorPriceRepo;
        this.em = em;
        this.numberGen = numberGen;
        this.approvalService = approvalService;
        this.mapper = mapper;
        this.purchaseOrderService = purchaseOrderService;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    public PurchaseRequisitionDto create(PurchaseRequisitionCreateDto dto) {
        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPrNumber(numberGen.next());
        pr.setRequestDate(dto.requestDate() != null ? dto.requestDate() : new Date());
        applyHeader(pr, dto.requiredByDate(),
                StringUtils.hasText(dto.requestedBy()) ? dto.requestedBy() : currentUsername(),
                dto.department(), dto.workCenterId(), dto.priority(), dto.notes());
        pr.setSource(dto.source() != null ? dto.source() : PurchaseRequisitionSource.MANUAL);
        pr.setSourceRefId(dto.sourceRefId());
        pr.setSourceRefNumber(dto.sourceRefNumber());
        pr.setItems(buildItems(pr, dto.items()));
        recalculateTotal(pr);
        return mapper.toDto(prRepo.save(pr));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PurchaseRequisitionDto getById(Long id) {
        return mapper.toDto(findActive(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseRequisitionListDto> list(String status, String approvalStatus,
                                                 String source, Pageable pageable) {
        if (StringUtils.hasText(status)) {
            return prRepo.findByStatusAndDeletedDateIsNull(
                    PurchaseRequisitionStatus.valueOf(status), pageable).map(mapper::toListDto);
        }
        if (StringUtils.hasText(approvalStatus)) {
            return prRepo.findByApprovalStatusAndDeletedDateIsNull(
                    PurchaseRequisitionApprovalStatus.valueOf(approvalStatus), pageable).map(mapper::toListDto);
        }
        return prRepo.findByDeletedDateIsNull(pageable).map(mapper::toListDto);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Override
    public PurchaseRequisitionDto update(Long id, PurchaseRequisitionUpdateDto dto) {
        PurchaseRequisition pr = findActive(id);
        assertDraft(pr);
        applyHeader(pr, dto.requiredByDate(), dto.requestedBy(), dto.department(),
                dto.workCenterId(), dto.priority(), dto.notes());
        if (dto.requestDate() != null) pr.setRequestDate(dto.requestDate());
        if (dto.items() != null) {
            pr.getItems().clear();
            pr.getItems().addAll(buildItems(pr, dto.items()));
        }
        recalculateTotal(pr);
        return mapper.toDto(prRepo.save(pr));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    public void delete(Long id) {
        PurchaseRequisition pr = findActive(id);
        assertDraft(pr);
        pr.setDeletedDate(new Date());
        prRepo.save(pr);
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @Override
    public PurchaseRequisitionDto submit(Long id) {
        PurchaseRequisition pr = findActive(id);
        if (pr.getApprovalStatus() != PurchaseRequisitionApprovalStatus.DRAFT &&
                pr.getApprovalStatus() != PurchaseRequisitionApprovalStatus.REJECTED) {
            throw new InvalidPurchaseRequisitionStateException(
                    "Only DRAFT or REJECTED requisitions can be submitted for approval.");
        }
        if (pr.getItems() == null || pr.getItems().isEmpty()) {
            throw new InvalidPurchaseRequisitionStateException(
                    "Cannot submit a requisition with no line items.");
        }
        pr.setApprovalStatus(PurchaseRequisitionApprovalStatus.PENDING_APPROVAL);
        return mapper.toDto(prRepo.save(pr));
    }

    @Override
    public PurchaseRequisitionDto approve(Long id) {
        PurchaseRequisition pr = findActive(id);
        approvalService.approve(pr);
        return mapper.toDto(prRepo.save(pr));
    }

    @Override
    public PurchaseRequisitionDto reject(Long id, PurchaseRequisitionApprovalActionDto dto) {
        PurchaseRequisition pr = findActive(id);
        approvalService.reject(pr, dto.reason());
        return mapper.toDto(prRepo.save(pr));
    }

    @Override
    public PurchaseRequisitionDto cancel(Long id, PurchaseRequisitionApprovalActionDto dto) {
        PurchaseRequisition pr = findActive(id);
        if (pr.getStatus() == PurchaseRequisitionStatus.CLOSED ||
                pr.getStatus() == PurchaseRequisitionStatus.CANCELLED) {
            throw new InvalidPurchaseRequisitionStateException(
                    "Cannot cancel a CLOSED or already CANCELLED requisition.");
        }
        pr.setStatus(PurchaseRequisitionStatus.CANCELLED);
        if (StringUtils.hasText(dto.reason())) {
            String prev = pr.getNotes();
            pr.setNotes((prev == null ? "" : prev + "\n") + "Cancelled: " + dto.reason());
        }
        for (PurchaseRequisitionItem line : pr.getItems()) {
            if (line.getLineStatus() == PurchaseRequisitionItemStatus.PENDING) {
                line.setLineStatus(PurchaseRequisitionItemStatus.CANCELLED);
            }
        }
        return mapper.toDto(prRepo.save(pr));
    }

    // ── Convert to PO ─────────────────────────────────────────────────────────

    @Override
    public PurchaseOrderDto convertToPurchaseOrder(Long id, ConvertToPoRequestDto dto) {
        PurchaseRequisition pr = findActive(id);
        if (pr.getApprovalStatus() != PurchaseRequisitionApprovalStatus.APPROVED) {
            throw new InvalidPurchaseRequisitionStateException(
                    "Only APPROVED requisitions can be converted to PO.");
        }
        if (dto.vendorId() == null) {
            throw new InvalidPurchaseRequisitionStateException("Vendor is required.");
        }

        Set<Long> selectedIds = dto.requisitionItemIds() == null
                ? new HashSet<>()
                : new HashSet<>(dto.requisitionItemIds());

        List<PurchaseRequisitionItem> linesToConvert = pr.getItems().stream()
                .filter(l -> l.getLineStatus() == PurchaseRequisitionItemStatus.PENDING)
                .filter(l -> selectedIds.isEmpty() || selectedIds.contains(l.getId()))
                .toList();

        if (linesToConvert.isEmpty()) {
            throw new InvalidPurchaseRequisitionStateException("No eligible lines to convert.");
        }

        List<PurchaseOrderItemCreateDto> poItems = new ArrayList<>();
        int line = 1;
        for (PurchaseRequisitionItem l : linesToConvert) {
            poItems.add(new PurchaseOrderItemCreateDto(
                    (long) l.getItem().getInventoryItemId(),
                    line++,
                    l.getDescription(),
                    l.getQuantity(),
                    l.getEstimatedUnitPrice() != null ? l.getEstimatedUnitPrice() : BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    l.getRequiredByDate(),
                    null,
                    l.getNotes()
            ));
        }

        PurchaseOrderCreateDto poDto = new PurchaseOrderCreateDto(
                dto.vendorId(),
                PurchaseOrderType.STANDARD,
                dto.orderDate() != null ? dto.orderDate() : new Date(),
                dto.expectedDeliveryDate(),
                null, "INR", BigDecimal.ONE,
                null, null, null, null, null,
                null, null,   // quotationNumber, quotationDate
                poItems,
                null,
                "Generated from requisition " + pr.getPrNumber(),
                null
        );

        PurchaseOrderDto created = purchaseOrderService.create(poDto);

        for (PurchaseRequisitionItem l : linesToConvert) {
            l.setLineStatus(PurchaseRequisitionItemStatus.CONVERTED);
            l.setPurchaseOrderId(created.id());
        }

        boolean allDone = pr.getItems().stream()
                .allMatch(i -> i.getLineStatus() != PurchaseRequisitionItemStatus.PENDING);
        if (allDone) pr.setStatus(PurchaseRequisitionStatus.CLOSED);

        prRepo.save(pr);
        return created;
    }

    // ── Generate from Work Order ──────────────────────────────────────────────

    @Override
    public PurchaseRequisitionDto generateFromWorkOrder(Long workOrderId) {
        int woId = workOrderId.intValue();
        WorkOrder wo = workOrderRepo.findById(woId)
                .orElseThrow(() -> new IllegalArgumentException("Work Order not found: " + workOrderId));

        List<WorkOrderMaterial> materials = workOrderMaterialRepo.findByWorkOrderId(woId);
        if (materials == null || materials.isEmpty()) {
            throw new InvalidPurchaseRequisitionStateException(
                    "Work Order " + workOrderId + " has no materials.");
        }

        PurchaseRequisition pr = new PurchaseRequisition();
        pr.setPrNumber(numberGen.next());
        pr.setRequestDate(new Date());
        pr.setRequestedBy(currentUsername());
        pr.setSource(PurchaseRequisitionSource.WORK_ORDER);
        pr.setSourceRefId(workOrderId);
        pr.setSourceRefNumber(wo.getWorkOrderNumber());
        pr.setPriority(PurchaseRequisitionPriority.NORMAL);
        pr.setNotes("Auto-generated from Work Order " + wo.getWorkOrderNumber());

        List<PurchaseRequisitionItem> lines = new ArrayList<>();
        int lineNo = 1;
        for (WorkOrderMaterial m : materials) {
            BigDecimal needed = m.getPlannedRequiredQuantity() == null
                    ? BigDecimal.ZERO : m.getPlannedRequiredQuantity();
            BigDecimal issued = m.getIssuedQuantity() == null
                    ? BigDecimal.ZERO : m.getIssuedQuantity();
            BigDecimal shortfall = needed.subtract(issued);
            if (shortfall.compareTo(BigDecimal.ZERO) <= 0) continue;

            PurchaseRequisitionItem line = new PurchaseRequisitionItem();
            line.setRequisition(pr);
            line.setLineNumber(lineNo++);
            line.setItem(m.getComponent());
            line.setDescription(m.getComponent() != null ? m.getComponent().getName() : null);
            line.setUom(m.getComponent() != null && m.getComponent().getUom() != null
                    ? m.getComponent().getUom().name() : null);
            line.setQuantity(shortfall.doubleValue());
            line.setRequiredByDate(wo.getPlannedStartDate());

            // Auto-fill price + vendor from ItemVendorPrice
            if (m.getComponent() != null) {
                int compId = m.getComponent().getInventoryItemId();
                ItemVendorPrice vp = vendorPriceRepo
                        .findByInventoryItem_InventoryItemIdAndPriceTypeAndIsPreferredVendorTrueAndDeletedDateIsNull(
                                compId, PriceType.PURCHASE)
                        .orElseGet(() -> {
                            List<ItemVendorPrice> c = vendorPriceRepo.findCheapestByItemAndType(compId, PriceType.PURCHASE);
                            return c.isEmpty() ? null : c.get(0);
                        });
                if (vp != null) {
                    line.setSuggestedVendor(vp.getVendor());
                    line.setEstimatedUnitPrice(vp.getPricePerUnit());
                    line.setEstimatedAmount(vp.getPricePerUnit().multiply(shortfall).setScale(2, RoundingMode.HALF_UP));
                } else if (m.getComponent().getProductFinanceSettings() != null
                        && m.getComponent().getProductFinanceSettings().getLastPurchaseCost() != null) {
                    BigDecimal lpc = BigDecimal.valueOf(m.getComponent().getProductFinanceSettings().getLastPurchaseCost());
                    line.setEstimatedUnitPrice(lpc);
                    line.setEstimatedAmount(lpc.multiply(shortfall).setScale(2, RoundingMode.HALF_UP));
                } else {
                    line.setEstimatedUnitPrice(BigDecimal.ZERO);
                    line.setEstimatedAmount(BigDecimal.ZERO);
                }
            } else {
                line.setEstimatedUnitPrice(BigDecimal.ZERO);
                line.setEstimatedAmount(BigDecimal.ZERO);
            }
            line.setWorkOrderMaterialId(m.getId());
            line.setLineStatus(PurchaseRequisitionItemStatus.PENDING);
            lines.add(line);
        }

        if (lines.isEmpty()) {
            throw new InvalidPurchaseRequisitionStateException(
                    "No material shortfall on Work Order " + wo.getWorkOrderNumber());
        }
        pr.setItems(lines);
        recalculateTotal(pr);
        return mapper.toDto(prRepo.save(pr));
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public String nextNumber() {
        return numberGen.preview();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PurchaseRequisition findActive(Long id) {
        return prRepo.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new PurchaseRequisitionNotFoundException(id));
    }

    private void assertDraft(PurchaseRequisition pr) {
        if (pr.getApprovalStatus() != PurchaseRequisitionApprovalStatus.DRAFT &&
                pr.getApprovalStatus() != PurchaseRequisitionApprovalStatus.REJECTED) {
            throw new InvalidPurchaseRequisitionStateException(
                    "PR " + pr.getPrNumber() + " is not editable in its current state.");
        }
    }

    private void applyHeader(PurchaseRequisition pr, Date requiredByDate, String requestedBy,
                             String department, Integer workCenterId,
                             PurchaseRequisitionPriority priority, String notes) {
        if (requiredByDate != null) pr.setRequiredByDate(requiredByDate);
        if (StringUtils.hasText(requestedBy)) pr.setRequestedBy(requestedBy);
        if (department != null) pr.setDepartment(department);
        if (workCenterId != null) {
            WorkCenter wc = workCenterRepo.findById(workCenterId)
                    .orElseThrow(() -> new IllegalArgumentException("Work Center not found: " + workCenterId));
            pr.setWorkCenter(wc);
        } else {
            pr.setWorkCenter(null);
        }
        if (priority != null) pr.setPriority(priority);
        if (notes != null) pr.setNotes(notes);
    }

    private List<PurchaseRequisitionItem> buildItems(PurchaseRequisition pr,
                                                    List<PurchaseRequisitionItemCreateDto> dtos) {
        if (dtos == null) return new ArrayList<>();
        List<PurchaseRequisitionItem> lines = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseRequisitionItemCreateDto d : dtos) {
            InventoryItem invItem = itemRepo.findById(d.itemId().intValue())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + d.itemId()));

            PurchaseRequisitionItem line = new PurchaseRequisitionItem();
            line.setRequisition(pr);
            line.setLineNumber(d.lineNumber() != null ? d.lineNumber() : lineNo);
            line.setItem(invItem);
            line.setDescription(StringUtils.hasText(d.description()) ? d.description() : invItem.getName());
            line.setUom(invItem.getUom() != null ? invItem.getUom().name() : null);
            line.setQuantity(d.quantity());
            line.setRequiredByDate(d.requiredByDate());
            // Auto-fill suggested vendor + price from ItemVendorPrice if caller didn't supply them
            ItemVendorPrice preferredVvp = vendorPriceRepo
                    .findByInventoryItem_InventoryItemIdAndPriceTypeAndIsPreferredVendorTrueAndDeletedDateIsNull(
                            invItem.getInventoryItemId(), PriceType.PURCHASE)
                    .orElse(null);
            if (preferredVvp == null) {
                List<ItemVendorPrice> cheapest = vendorPriceRepo.findCheapestByItemAndType(
                        invItem.getInventoryItemId(), PriceType.PURCHASE);
                preferredVvp = cheapest.isEmpty() ? null : cheapest.get(0);
            }

            if (d.suggestedVendorId() != null) {
                Contact v = contactRepo.findById(d.suggestedVendorId())
                        .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + d.suggestedVendorId()));
                line.setSuggestedVendor(v);
            } else if (preferredVvp != null) {
                line.setSuggestedVendor(preferredVvp.getVendor());
            }

            BigDecimal price;
            if (d.estimatedUnitPrice() != null && d.estimatedUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                price = d.estimatedUnitPrice();
            } else if (preferredVvp != null) {
                price = preferredVvp.getPricePerUnit();
            } else if (invItem.getProductFinanceSettings() != null
                    && invItem.getProductFinanceSettings().getLastPurchaseCost() != null) {
                price = BigDecimal.valueOf(invItem.getProductFinanceSettings().getLastPurchaseCost());
            } else {
                price = BigDecimal.ZERO;
            }
            line.setEstimatedUnitPrice(price);
            line.setEstimatedAmount(price.multiply(BigDecimal.valueOf(d.quantity()))
                    .setScale(2, RoundingMode.HALF_UP));
            line.setWorkOrderMaterialId(d.workOrderMaterialId());
            line.setNotes(d.notes());
            line.setLineStatus(PurchaseRequisitionItemStatus.PENDING);
            lines.add(line);
            lineNo++;
        }
        return lines;
    }

    private void recalculateTotal(PurchaseRequisition pr) {
        BigDecimal total = BigDecimal.ZERO;
        if (pr.getItems() != null) {
            for (PurchaseRequisitionItem l : pr.getItems()) {
                if (l.getEstimatedAmount() != null) total = total.add(l.getEstimatedAmount());
            }
        }
        pr.setTotalEstimatedAmount(total);
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
