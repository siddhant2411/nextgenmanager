package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionService;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.common.events.DocumentVoidedEvent;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.sales.dto.*;
import com.nextgenmanager.nextgenmanager.sales.events.SalesCreditNoteConfirmedEvent;
import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNote;
import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNoteItem;
import com.nextgenmanager.nextgenmanager.sales.model.SalesCreditNoteStatus;
import com.nextgenmanager.nextgenmanager.sales.model.TaxInvoice;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesCreditNoteRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.TaxInvoiceRepository;
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
public class SalesCreditNoteServiceImpl implements SalesCreditNoteService {

    private final SalesCreditNoteRepository creditNoteRepo;
    private final ContactRepository contactRepo;
    private final InventoryItemRepository itemRepo;
    private final TaxInvoiceRepository taxInvoiceRepo;
    private final SalesCreditNoteNumberGenerator numberGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final InventoryTransactionService txService;

    public SalesCreditNoteServiceImpl(SalesCreditNoteRepository creditNoteRepo,
                                      ContactRepository contactRepo,
                                      InventoryItemRepository itemRepo,
                                      TaxInvoiceRepository taxInvoiceRepo,
                                      SalesCreditNoteNumberGenerator numberGenerator,
                                      DomainEventPublisher domainEventPublisher,
                                      InventoryTransactionService txService) {
        this.creditNoteRepo  = creditNoteRepo;
        this.contactRepo     = contactRepo;
        this.itemRepo        = itemRepo;
        this.taxInvoiceRepo  = taxInvoiceRepo;
        this.numberGenerator = numberGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.txService       = txService;
    }

    @Override
    public SalesCreditNoteResponseDTO create(CreateSalesCreditNoteRequest req) {
        TaxInvoice taxInvoice = null;
        if (req.getTaxInvoiceId() != null) {
            taxInvoice = taxInvoiceRepo.findByIdAndDeletedDateIsNull(req.getTaxInvoiceId())
                    .orElseThrow(() -> new IllegalArgumentException("Tax invoice not found: " + req.getTaxInvoiceId()));
        }

        // Customer is taken explicitly, or derived from the linked invoice's sales order.
        Contact customer;
        if (req.getCustomerId() != null) {
            customer = contactRepo.findById(req.getCustomerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + req.getCustomerId()));
        } else if (taxInvoice != null && taxInvoice.getSalesOrder() != null) {
            customer = taxInvoice.getSalesOrder().getCustomer();
            if (customer == null) {
                throw new IllegalArgumentException("Invoice " + taxInvoice.getInvoiceNumber() + " has no customer.");
            }
        } else {
            throw new IllegalArgumentException("Either customerId or taxInvoiceId is required.");
        }

        SalesCreditNote cn = new SalesCreditNote();
        cn.setCreditNoteNumber(numberGenerator.next());
        cn.setCreditNoteDate(req.getCreditNoteDate() != null ? req.getCreditNoteDate() : LocalDate.now());
        cn.setCustomer(customer);
        cn.setTaxInvoice(taxInvoice);
        cn.setReason(req.getReason());
        cn.setStatus(SalesCreditNoteStatus.DRAFT);
        cn.setRemarks(req.getRemarks());
        cn.setCreatedBy(currentUser(req.getCreatedBy()));

        double subtotal = 0, totalGst = 0;
        if (req.getItems() != null) {
            for (SalesCreditNoteLineRequest line : req.getItems()) {
                InventoryItem item = itemRepo.findById(line.getInventoryItemId())
                        .orElseThrow(() -> new IllegalArgumentException("Item not found: " + line.getInventoryItemId()));

                double base      = line.getReturnedQty() * line.getRate();
                double gstAmt    = base * (line.getGstRate() / 100.0);
                double lineTotal = base + gstAmt;

                SalesCreditNoteItem cni = new SalesCreditNoteItem();
                cni.setSalesCreditNote(cn);
                cni.setInventoryItem(item);
                cni.setLineNumber(line.getLineNumber());
                cni.setReturnedQty(line.getReturnedQty());
                cni.setRate(line.getRate());
                cni.setGstRate(line.getGstRate());
                cni.setGstAmount(gstAmt);
                cni.setTotalAmount(lineTotal);
                cni.setWarehouseTo(line.getWarehouseTo());
                cni.setRemarks(line.getRemarks());

                cn.getItems().add(cni);
                subtotal += base;
                totalGst += gstAmt;
            }
        }

        cn.setSubtotal(subtotal);
        cn.setTotalGstAmount(totalGst);
        cn.setTotalAmount(subtotal + totalGst);

        return toResponseDTO(creditNoteRepo.save(cn));
    }

    @Override
    public SalesCreditNoteResponseDTO confirm(Long id) {
        SalesCreditNote cn = loadActive(id);
        if (cn.getStatus() != SalesCreditNoteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT credit notes can be confirmed. Current status: " + cn.getStatus());
        }
        // Restock returned goods at standard cost so perpetual inventory stays in sync.
        // The companion SALES_RETURN inventory movement auto-posts Dr FG Stock / Cr COGS.
        for (SalesCreditNoteItem line : cn.getItems()) {
            InventoryTransactionDTO dto = new InventoryTransactionDTO();
            dto.setInventoryItemId(line.getInventoryItem().getInventoryItemId());
            dto.setQuantity(line.getReturnedQty());   // positive = stock comes back
            dto.setTransactionType("SALES_RETURN");
            dto.setReferenceType("SALES_CREDIT_NOTE");
            dto.setReferenceDocNo(cn.getCreditNoteNumber());
            dto.setWarehouse(line.getWarehouseTo());
            dto.setCreatedBy(cn.getCreatedBy());
            txService.adjustStock(dto);
        }
        cn.setStatus(SalesCreditNoteStatus.CONFIRMED);
        SalesCreditNote saved = creditNoteRepo.save(cn);

        // Accounting auto-posts the CREDIT_NOTE voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new SalesCreditNoteConfirmedEvent(saved.getId()));
        return toResponseDTO(saved);
    }

    @Override
    public SalesCreditNoteResponseDTO cancel(Long id) {
        SalesCreditNote cn = loadActive(id);
        if (cn.getStatus() == SalesCreditNoteStatus.CANCELLED) {
            throw new IllegalStateException("Credit note is already cancelled.");
        }
        cn.setStatus(SalesCreditNoteStatus.CANCELLED);
        cn.setDeletedDate(LocalDate.now());
        SalesCreditNote saved = creditNoteRepo.save(cn);
        // Accounting reverses the CREDIT_NOTE voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new DocumentVoidedEvent(SourceDocTypes.SALES_CREDIT_NOTE, id, "Credit note cancelled"));
        return toResponseDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesCreditNoteResponseDTO getById(Long id) {
        return toResponseDTO(loadActive(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SalesCreditNoteListDTO> getAll(Pageable pageable) {
        return creditNoteRepo.findByDeletedDateIsNull(pageable).map(this::toListDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesCreditNoteListDTO> getByCustomer(int customerId) {
        return creditNoteRepo.findByCustomer_IdAndDeletedDateIsNull(customerId)
                .stream().map(this::toListDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesCreditNoteListDTO> getByInvoice(Long taxInvoiceId) {
        return creditNoteRepo.findByTaxInvoice_IdAndDeletedDateIsNull(taxInvoiceId)
                .stream().map(this::toListDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public String nextNumber() {
        return numberGenerator.preview();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private SalesCreditNote loadActive(Long id) {
        return creditNoteRepo.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Sales Credit Note not found: " + id));
    }

    private String currentUser(String fallback) {
        try {
            String name = SecurityContextHolder.getContext().getAuthentication().getName();
            return (name != null && !name.isBlank()) ? name : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private SalesCreditNoteResponseDTO toResponseDTO(SalesCreditNote cn) {
        List<SalesCreditNoteItemDTO> itemDTOs = cn.getItems().stream().map(i -> new SalesCreditNoteItemDTO(
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
                i.getWarehouseTo(),
                i.getRemarks()
        )).collect(Collectors.toList());

        return new SalesCreditNoteResponseDTO(
                cn.getId(),
                cn.getCreditNoteNumber(),
                cn.getCreditNoteDate(),
                cn.getCustomer() != null ? cn.getCustomer().getId() : 0,
                cn.getCustomer() != null ? cn.getCustomer().getCompanyName() : null,
                cn.getTaxInvoice() != null ? cn.getTaxInvoice().getId() : null,
                cn.getTaxInvoice() != null ? cn.getTaxInvoice().getInvoiceNumber() : null,
                cn.getReason(),
                cn.getStatus(),
                cn.getRemarks(),
                cn.getSubtotal(),
                cn.getTotalGstAmount(),
                cn.getTotalAmount(),
                cn.getCreatedBy(),
                cn.getCreatedDate(),
                itemDTOs
        );
    }

    private SalesCreditNoteListDTO toListDTO(SalesCreditNote cn) {
        return new SalesCreditNoteListDTO(
                cn.getId(),
                cn.getCreditNoteNumber(),
                cn.getCreditNoteDate(),
                cn.getCustomer() != null ? cn.getCustomer().getCompanyName() : null,
                cn.getTaxInvoice() != null ? cn.getTaxInvoice().getInvoiceNumber() : null,
                cn.getReason(),
                cn.getStatus(),
                cn.getTotalAmount(),
                cn.getCreatedDate()
        );
    }
}
