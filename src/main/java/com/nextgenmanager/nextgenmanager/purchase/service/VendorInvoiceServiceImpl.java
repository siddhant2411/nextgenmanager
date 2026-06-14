package com.nextgenmanager.nextgenmanager.purchase.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptItem;
import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.Inventory.repository.GoodsReceiptNoteRepository;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.service.FileStorageService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.purchase.dto.CreateVendorInvoiceRequest;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceItemRequest;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceListDto;
import com.nextgenmanager.nextgenmanager.purchase.exception.InvalidPurchaseOrderStateException;
import com.nextgenmanager.nextgenmanager.purchase.exception.PurchaseOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.purchase.mapper.VendorInvoiceMapper;
import com.nextgenmanager.nextgenmanager.purchase.model.*;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import com.nextgenmanager.nextgenmanager.purchase.repository.VendorInvoiceRepository;
import com.nextgenmanager.nextgenmanager.purchase.events.VendorInvoicePostedEvent;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.common.events.DocumentVoidedEvent;
import com.nextgenmanager.nextgenmanager.common.events.SourceDocTypes;
import io.minio.GetObjectResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class VendorInvoiceServiceImpl implements VendorInvoiceService {

    private final VendorInvoiceRepository invoiceRepo;
    private final PurchaseOrderRepository poRepo;
    private final GoodsReceiptNoteRepository grnRepo;
    private final InventoryItemRepository itemRepo;
    private final VendorInvoiceMapper mapper;
    private final DomainEventPublisher domainEventPublisher;
    private final FileStorageService fileStorageService;

    public VendorInvoiceServiceImpl(VendorInvoiceRepository invoiceRepo,
                                    PurchaseOrderRepository poRepo,
                                    GoodsReceiptNoteRepository grnRepo,
                                    InventoryItemRepository itemRepo,
                                    VendorInvoiceMapper mapper,
                                    DomainEventPublisher domainEventPublisher,
                                    FileStorageService fileStorageService) {
        this.invoiceRepo = invoiceRepo;
        this.poRepo      = poRepo;
        this.grnRepo     = grnRepo;
        this.itemRepo    = itemRepo;
        this.mapper      = mapper;
        this.domainEventPublisher = domainEventPublisher;
        this.fileStorageService   = fileStorageService;
    }

    @Override
    public VendorInvoiceDto create(CreateVendorInvoiceRequest req) {
        if (invoiceRepo.existsByInvoiceNumber(req.invoiceNumber())) {
            throw new IllegalArgumentException("Invoice number already exists: " + req.invoiceNumber());
        }

        PurchaseOrder po = poRepo.findByIdAndDeletedDateIsNull(req.poId())
                .orElseThrow(() -> new PurchaseOrderNotFoundException(req.poId()));

        GoodsReceiptNote grn = null;
        if (req.grnId() != null) {
            grn = grnRepo.findById(req.grnId())
                    .orElseThrow(() -> new IllegalArgumentException("GRN not found: " + req.grnId()));
        }

        VendorInvoice invoice = new VendorInvoice();
        invoice.setInvoiceNumber(req.invoiceNumber());
        invoice.setInvoiceDate(req.invoiceDate());
        invoice.setPurchaseOrder(po);
        invoice.setVendor(po.getVendor());
        invoice.setGrn(grn);
        invoice.setStatus(VendorInvoiceStatus.DRAFT);
        invoice.setSubtotal(orZero(req.subtotal()));
        invoice.setCgstAmount(orZero(req.cgstAmount()));
        invoice.setSgstAmount(orZero(req.sgstAmount()));
        invoice.setIgstAmount(orZero(req.igstAmount()));
        invoice.setCessAmount(orZero(req.cessAmount()));
        invoice.setGrandTotal(orZero(req.grandTotal()));
        invoice.setRemarks(req.remarks());
        invoice.setCreatedBy(currentUser());
        invoice.setItems(buildItems(invoice, req.items()));

        // Mismatch checks
        BigDecimal tolerance = BigDecimal.ONE;
        boolean amountMismatch = po.getGrandTotal().subtract(invoice.getGrandTotal()).abs()
                .compareTo(tolerance) > 0;
        invoice.setAmountMismatch(amountMismatch);

        boolean qtyMismatch = false;
        if (grn != null) {
            Map<Long, Double> receivedByItemId = grn.getItems().stream()
                    .filter(gi -> gi.getItem() != null)
                    .collect(Collectors.toMap(
                            gi -> (long) gi.getItem().getInventoryItemId(),
                            GoodsReceiptItem::getReceivedQty,
                            Double::sum));
            for (VendorInvoiceItem line : invoice.getItems()) {
                if (line.getItem() == null) continue;
                double received = receivedByItemId.getOrDefault(
                        (long) line.getItem().getInventoryItemId(), 0.0);
                if (line.getInvoicedQty() > received + 1e-9) {
                    qtyMismatch = true;
                    break;
                }
            }
        }
        invoice.setQtyMismatch(qtyMismatch);

        VendorInvoice saved = invoiceRepo.save(invoice);

        // Auto-complete PO when RECEIVED with no mismatches
        if (po.getStatus() == PurchaseOrderStatus.RECEIVED && !amountMismatch && !qtyMismatch) {
            po.setStatus(PurchaseOrderStatus.COMPLETED);
            poRepo.save(po);
        }

        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorInvoiceDto getById(Long id) {
        return mapper.toDto(findActive(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorInvoiceListDto> getByPo(Long poId) {
        return invoiceRepo.findByPurchaseOrderIdAndDeletedDateIsNull(poId).stream()
                .map(mapper::toListDto)
                .toList();
    }

    @Override
    public VendorInvoiceDto post(Long id) {
        VendorInvoice invoice = findActive(id);
        if (invoice.getStatus() != VendorInvoiceStatus.DRAFT) {
            throw new InvalidPurchaseOrderStateException("Only DRAFT invoices can be posted.");
        }
        invoice.setStatus(VendorInvoiceStatus.POSTED);
        VendorInvoice saved = invoiceRepo.save(invoice);

        // Accounting auto-posts the PURCHASE voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new VendorInvoicePostedEvent(saved.getId()));
        return mapper.toDto(saved);
    }

    @Override
    public VendorInvoiceDto cancel(Long id) {
        VendorInvoice invoice = findActive(id);
        if (invoice.getStatus() == VendorInvoiceStatus.CANCELLED) {
            throw new InvalidPurchaseOrderStateException("Invoice is already cancelled.");
        }
        invoice.setStatus(VendorInvoiceStatus.CANCELLED);
        VendorInvoice saved = invoiceRepo.save(invoice);
        // Accounting reverses the PURCHASE voucher (listener runs after this tx commits).
        domainEventPublisher.publish(new DocumentVoidedEvent(SourceDocTypes.VENDOR_INVOICE, id, "Vendor invoice cancelled"));
        return mapper.toDto(saved);
    }

    // ── Attachments ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<FileAttachment> getAttachments(Long invoiceId) {
        return fileStorageService.findAttachmentsByTypeAndId("VendorInvoice", invoiceId);
    }

    @Override
    @Transactional(readOnly = true)
    public FileAttachment getAttachment(Long fileId) {
        return fileStorageService.getFileById(fileId);
    }

    @Override
    public void uploadAttachment(Long invoiceId, MultipartFile file) throws Exception {
        findActive(invoiceId); // validate invoice exists
        fileStorageService.uploadFile(file, "vendor-invoice", "VendorInvoice", invoiceId, currentUser());
    }

    @Override
    public void deleteAttachment(Long fileId) throws Exception {
        fileStorageService.deleteAttachment(fileId);
    }

    @Override
    public GetObjectResponse downloadAttachment(Long fileId) throws Exception {
        return fileStorageService.downloadById(fileId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VendorInvoice findActive(Long id) {
        return invoiceRepo.findByIdAndDeletedDateIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Vendor invoice not found: " + id));
    }

    private List<VendorInvoiceItem> buildItems(VendorInvoice invoice,
                                               List<VendorInvoiceItemRequest> dtos) {
        if (dtos == null) return new ArrayList<>();
        List<VendorInvoiceItem> lines = new ArrayList<>();
        for (VendorInvoiceItemRequest d : dtos) {
            VendorInvoiceItem line = new VendorInvoiceItem();
            line.setInvoice(invoice);
            if (d.itemId() != null) {
                InventoryItem item = itemRepo.findById(d.itemId().intValue())
                        .orElseThrow(() -> new IllegalArgumentException("Item not found: " + d.itemId()));
                line.setItem(item);
            }
            line.setHsnCode(d.hsnCode());
            line.setUom(d.uom());
            line.setInvoicedQty(d.invoicedQty());
            line.setUnitPrice(orZero(d.unitPrice()));
            line.setTaxableValue(orZero(d.taxableValue()));
            line.setCgstAmount(orZero(d.cgstAmount()));
            line.setSgstAmount(orZero(d.sgstAmount()));
            line.setIgstAmount(orZero(d.igstAmount()));
            line.setCessAmount(orZero(d.cessAmount()));
            line.setLineTotal(orZero(d.lineTotal()));
            lines.add(line);
        }
        return lines;
    }

    private BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }
}
