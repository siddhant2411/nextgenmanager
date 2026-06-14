package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.Inventory.repository.GoodsReceiptNoteRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionService;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.purchase.dto.*;
import com.nextgenmanager.nextgenmanager.purchase.model.*;
import com.nextgenmanager.nextgenmanager.purchase.repository.DebitNoteRepository;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import com.nextgenmanager.nextgenmanager.purchase.events.DebitNoteConfirmedEvent;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.common.events.DocumentVoidedEvent;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DebitNoteServiceImpl implements DebitNoteService {

    private final DebitNoteRepository debitNoteRepo;
    private final PurchaseOrderRepository poRepo;
    private final GoodsReceiptNoteRepository grnRepo;
    private final ContactRepository contactRepo;
    private final InventoryItemRepository itemRepo;
    private final InventoryTransactionService txService;
    private final DebitNoteNumberGenerator numberGenerator;
    private final DomainEventPublisher domainEventPublisher;

    public DebitNoteServiceImpl(DebitNoteRepository debitNoteRepo,
                                PurchaseOrderRepository poRepo,
                                GoodsReceiptNoteRepository grnRepo,
                                ContactRepository contactRepo,
                                InventoryItemRepository itemRepo,
                                InventoryTransactionService txService,
                                DebitNoteNumberGenerator numberGenerator,
                                DomainEventPublisher domainEventPublisher) {
        this.debitNoteRepo   = debitNoteRepo;
        this.poRepo          = poRepo;
        this.grnRepo         = grnRepo;
        this.contactRepo     = contactRepo;
        this.itemRepo        = itemRepo;
        this.txService       = txService;
        this.numberGenerator = numberGenerator;
        this.domainEventPublisher = domainEventPublisher;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE (DRAFT)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DebitNoteResponseDTO create(CreateDebitNoteRequest req) {
        // Resolve vendor
        Contact vendor = contactRepo.findById(req.getVendorId())
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + req.getVendorId()));

        // Resolve optional PO
        PurchaseOrder po = null;
        if (req.getPurchaseOrderId() != null) {
            po = poRepo.findByIdAndDeletedDateIsNull(req.getPurchaseOrderId())
                    .orElseThrow(() -> new IllegalArgumentException("Purchase Order not found: " + req.getPurchaseOrderId()));
        }

        // Resolve optional GRN
        GoodsReceiptNote grn = null;
        if (req.getGrnId() != null) {
            Long grnId = req.getGrnId();
            grn = grnRepo.findById(grnId)
                    .orElseThrow(() -> new IllegalArgumentException("GRN not found: " + grnId));
        }

        // Parse return reason
        ReturnReason reason = ReturnReason.OTHER;
        if (req.getReturnReason() != null) {
            try { reason = ReturnReason.valueOf(req.getReturnReason().toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        // Build header
        DebitNote dn = new DebitNote();
        dn.setDebitNoteNumber(numberGenerator.next());
        dn.setDebitNoteDate(req.getDebitNoteDate() != null ? req.getDebitNoteDate() : LocalDate.now());
        dn.setVendor(vendor);
        dn.setPurchaseOrder(po);
        dn.setGrn(grn);
        dn.setReturnReason(reason);
        dn.setStatus(DebitNoteStatus.DRAFT);
        dn.setRemarks(req.getRemarks());

        String user = currentUser(req.getCreatedBy());
        dn.setCreatedBy(user);

        // Build line items
        double subtotal = 0, totalGst = 0;
        for (DebitNoteLineRequest line : req.getItems()) {
            InventoryItem item = itemRepo.findById(line.getInventoryItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getInventoryItemId()));

            double base    = line.getReturnedQty() * line.getRate();
            double gstAmt  = base * (line.getGstRate() / 100.0);
            double lineTotal = base + gstAmt;

            DebitNoteItem dni = new DebitNoteItem();
            dni.setDebitNote(dn);
            dni.setInventoryItem(item);
            dni.setLineNumber(line.getLineNumber());
            dni.setReturnedQty(line.getReturnedQty());
            dni.setRate(line.getRate());
            dni.setGstRate(line.getGstRate());
            dni.setGstAmount(gstAmt);
            dni.setTotalAmount(lineTotal);
            dni.setWarehouseFrom(line.getWarehouseFrom());
            dni.setRemarks(line.getRemarks());

            dn.getItems().add(dni);
            subtotal  += base;
            totalGst  += gstAmt;
        }

        dn.setSubtotal(subtotal);
        dn.setTotalGstAmount(totalGst);
        dn.setTotalAmount(subtotal + totalGst);

        DebitNote saved = debitNoteRepo.save(dn);
        return toResponseDTO(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONFIRM — triggers PURCHASE_RETURN stock adjustment (negative qty)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DebitNoteResponseDTO confirm(Long id) {
        DebitNote dn = loadActive(id);
        if (dn.getStatus() != DebitNoteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT debit notes can be confirmed. Current status: " + dn.getStatus());
        }

        // Reduce stock for each returned item
        for (DebitNoteItem line : dn.getItems()) {
            InventoryTransactionDTO dto = new InventoryTransactionDTO();
            dto.setInventoryItemId(line.getInventoryItem().getInventoryItemId());
            dto.setQuantity(-line.getReturnedQty());           // negative = stock leaves the warehouse
            dto.setTransactionType("PURCHASE_RETURN");
            dto.setReferenceType("DEBIT_NOTE");
            dto.setReferenceDocNo(dn.getDebitNoteNumber());
            dto.setWarehouse(line.getWarehouseFrom());
            dto.setCostPerUnit(line.getRate());
            dto.setCreatedBy(dn.getCreatedBy());
            txService.adjustStock(dto);
        }

        dn.setStatus(DebitNoteStatus.CONFIRMED);
        DebitNote saved = debitNoteRepo.save(dn);

        // Accounting auto-posts the DEBIT_NOTE voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new DebitNoteConfirmedEvent(saved.getId()));
        return toResponseDTO(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CANCEL — reverses stock if was CONFIRMED
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public DebitNoteResponseDTO cancel(Long id) {
        DebitNote dn = loadActive(id);
        if (dn.getStatus() == DebitNoteStatus.CANCELLED) {
            throw new IllegalStateException("Debit note is already cancelled.");
        }

        // If it was confirmed, reverse the stock movement
        if (dn.getStatus() == DebitNoteStatus.CONFIRMED) {
            for (DebitNoteItem line : dn.getItems()) {
                InventoryTransactionDTO dto = new InventoryTransactionDTO();
                dto.setInventoryItemId(line.getInventoryItem().getInventoryItemId());
                dto.setQuantity(line.getReturnedQty());        // positive = stock comes back
                dto.setTransactionType("PURCHASE_RETURN_REVERSAL");
                dto.setReferenceType("DEBIT_NOTE");
                dto.setReferenceDocNo(dn.getDebitNoteNumber());
                dto.setWarehouse(line.getWarehouseFrom());
                dto.setCostPerUnit(line.getRate());
                dto.setCreatedBy(currentUser(null));
                txService.adjustStock(dto);
            }
        }

        dn.setStatus(DebitNoteStatus.CANCELLED);
        dn.setDeletedDate(LocalDate.now());
        DebitNote saved = debitNoteRepo.save(dn);
        // Accounting reverses the DEBIT_NOTE voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new DocumentVoidedEvent(SourceDocTypes.DEBIT_NOTE, id, "Debit note cancelled"));
        return toResponseDTO(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DebitNoteResponseDTO getById(Long id) {
        return toResponseDTO(loadActive(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DebitNoteListDTO> getAll(Pageable pageable) {
        return debitNoteRepo.findByDeletedDateIsNull(pageable).map(this::toListDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebitNoteListDTO> getByVendor(int vendorId) {
        return debitNoteRepo.findByVendor_IdAndDeletedDateIsNull(vendorId)
                .stream().map(this::toListDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebitNoteListDTO> getByGrn(Long grnId) {
        return debitNoteRepo.findByGrn_IdAndDeletedDateIsNull(grnId)
                .stream().map(this::toListDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebitNoteListDTO> getByPurchaseOrder(Long poId) {
        return debitNoteRepo.findByPurchaseOrder_IdAndDeletedDateIsNull(poId)
                .stream().map(this::toListDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public String nextNumber() {
        return numberGenerator.preview();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private DebitNote loadActive(Long id) {
        return debitNoteRepo.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Debit Note not found: " + id));
    }

    private String currentUser(String fallback) {
        try {
            String name = SecurityContextHolder.getContext().getAuthentication().getName();
            return (name != null && !name.isBlank()) ? name : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private DebitNoteResponseDTO toResponseDTO(DebitNote dn) {
        List<DebitNoteItemDTO> itemDTOs = dn.getItems().stream().map(i -> new DebitNoteItemDTO(
                i.getId(),
                i.getInventoryItem().getInventoryItemId(),
                i.getInventoryItem().getItemCode(),
                i.getInventoryItem().getName(),
                i.getLineNumber(),
                i.getReturnedQty(),
                i.getRate(),
                i.getGstRate(),
                i.getGstAmount(),
                i.getTotalAmount(),
                i.getWarehouseFrom(),
                i.getRemarks()
        )).collect(Collectors.toList());

        return new DebitNoteResponseDTO(
                dn.getId(),
                dn.getDebitNoteNumber(),
                dn.getDebitNoteDate(),
                dn.getVendor() != null ? dn.getVendor().getId() : 0,
                dn.getVendor() != null ? dn.getVendor().getCompanyName() : null,
                dn.getPurchaseOrder() != null ? dn.getPurchaseOrder().getId() : null,
                dn.getPurchaseOrder() != null ? dn.getPurchaseOrder().getPurchaseOrderNumber() : null,
                dn.getGrn() != null ? dn.getGrn().getId() : null,
                dn.getGrn() != null ? dn.getGrn().getGrnNumber() : null,
                dn.getReturnReason(),
                dn.getStatus(),
                dn.getRemarks(),
                dn.getSubtotal(),
                dn.getTotalGstAmount(),
                dn.getTotalAmount(),
                dn.getCreatedBy(),
                dn.getCreatedDate(),
                itemDTOs
        );
    }

    private DebitNoteListDTO toListDTO(DebitNote dn) {
        return new DebitNoteListDTO(
                dn.getId(),
                dn.getDebitNoteNumber(),
                dn.getDebitNoteDate(),
                dn.getVendor() != null ? dn.getVendor().getCompanyName() : null,
                dn.getPurchaseOrder() != null ? dn.getPurchaseOrder().getPurchaseOrderNumber() : null,
                dn.getGrn() != null ? dn.getGrn().getGrnNumber() : null,
                dn.getReturnReason(),
                dn.getStatus(),
                dn.getTotalAmount(),
                dn.getCreatedDate()
        );
    }
}
