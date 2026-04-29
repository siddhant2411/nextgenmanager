package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.CreateGRNRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.GRNLineItemDTO;
import com.nextgenmanager.nextgenmanager.Inventory.dto.GRNResponseDTO;
import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.GRNStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptItem;
import com.nextgenmanager.nextgenmanager.Inventory.model.GoodsReceiptNote;
import com.nextgenmanager.nextgenmanager.Inventory.repository.GoodsReceiptNoteRepository;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrder;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderItem;
import com.nextgenmanager.nextgenmanager.purchase.model.PurchaseOrderStatus;
import com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GRNServiceImpl implements GRNService {

    @Autowired private GoodsReceiptNoteRepository grnRepository;
    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private InventoryTransactionService inventoryTransactionService;

    @Override
    @Transactional
    public GRNResponseDTO createGRN(CreateGRNRequest request) {
        GoodsReceiptNote grn = new GoodsReceiptNote();
        grn.setGrnNumber(generateGrnNumber());
        grn.setGrnDate(request.getGrnDate() != null ? request.getGrnDate() : LocalDate.now());
        grn.setWarehouse(request.getWarehouse());
        grn.setRemarks(request.getRemarks());
        grn.setCreatedBy(request.getCreatedBy());
        grn.setStatus(GRNStatus.SUBMITTED);

        if (request.getPurchaseOrderId() != null) {
            PurchaseOrder po = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                    .orElseThrow(() -> new RuntimeException("Purchase Order not found: " + request.getPurchaseOrderId()));
            grn.setPurchaseOrder(po);
            if (grn.getVendor() == null && po.getVendor() != null) {
                grn.setVendor(po.getVendor());
            }
        }

        if (request.getVendorId() != null) {
            Contact vendor = contactRepository.findById(request.getVendorId().intValue())
                    .orElseThrow(() -> new RuntimeException("Vendor not found: " + request.getVendorId()));
            grn.setVendor(vendor);
        }

        List<GoodsReceiptItem> lineItems = new ArrayList<>();
        double totalAmount = 0;

        for (GRNLineItemDTO lineDto : request.getItems()) {
            InventoryItem item = inventoryItemRepository.findByActiveId(lineDto.getInventoryItemId());
            if (item == null) throw new RuntimeException("Inventory item not found: " + lineDto.getInventoryItemId());

            GoodsReceiptItem line = new GoodsReceiptItem();
            line.setGoodsReceiptNote(grn);
            line.setItem(item);
            line.setOrderedQty(lineDto.getOrderedQty());
            line.setReceivedQty(lineDto.getReceivedQty());
            line.setAcceptedQty(lineDto.getAcceptedQty());
            line.setRejectedQty(lineDto.getRejectedQty());
            line.setRate(lineDto.getRate());
            line.setAmount(lineDto.getAcceptedQty() * lineDto.getRate());
            line.setBatchNo(lineDto.getBatchNo());
            line.setExpiryDate(lineDto.getExpiryDate());
            line.setRejectionReason(lineDto.getRejectionReason());
            lineItems.add(line);
            totalAmount += line.getAmount();

            if (lineDto.getAcceptedQty() > 0) {
                InventoryTransactionDTO txn = new InventoryTransactionDTO();
                txn.setInventoryItemId(lineDto.getInventoryItemId());
                txn.setQuantity(lineDto.getAcceptedQty());
                txn.setTransactionType("GRN");
                txn.setReferenceType("GRN");
                txn.setReferenceDocNo(grn.getGrnNumber());
                txn.setWarehouse(request.getWarehouse());
                txn.setCostPerUnit(lineDto.getRate());
                txn.setCreatedBy(request.getCreatedBy());
                inventoryTransactionService.produceStock(txn);
            }
        }

        grn.setItems(lineItems);
        grn.setTotalAmount(totalAmount);
        GoodsReceiptNote saved = grnRepository.save(grn);

        updatePurchaseOrderStatus(saved);

        return toResponseDTO(saved);
    }

    @Override
    public GRNResponseDTO getGRN(Long grnId) {
        GoodsReceiptNote grn = grnRepository.findById(grnId)
                .orElseThrow(() -> new RuntimeException("GRN not found: " + grnId));
        return toResponseDTO(grn);
    }

    @Override
    public List<GRNResponseDTO> getGRNsByPurchaseOrder(Long purchaseOrderId) {
        return grnRepository.findByPurchaseOrder_Id(purchaseOrderId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Override
    public Page<GRNResponseDTO> searchGRNs(Long poId, String status, Long vendorId, String grnNumber, Pageable pageable) {
        GRNStatus grnStatus = null;
        if (status != null && !status.isBlank()) {
            grnStatus = GRNStatus.valueOf(status.toUpperCase());
        }
        return grnRepository.search(poId, grnStatus, vendorId, grnNumber, pageable)
                .map(this::toResponseDTO);
    }

    private void updatePurchaseOrderStatus(GoodsReceiptNote grn) {
        if (grn.getPurchaseOrder() == null) return;
        PurchaseOrder po = grn.getPurchaseOrder();

        List<GoodsReceiptNote> allGrns = grnRepository.findByPurchaseOrder_Id(po.getId());
        boolean allFulfilled = po.getItems().stream().allMatch(poItem -> {
            double totalReceived = allGrns.stream()
                    .flatMap(g -> g.getItems().stream())
                    .filter(gi -> gi.getItem().getInventoryItemId() == poItem.getItem().getInventoryItemId())
                    .mapToDouble(GoodsReceiptItem::getAcceptedQty)
                    .sum();
            return totalReceived >= poItem.getQuantityOrdered();
        });

        po.setStatus(allFulfilled ? PurchaseOrderStatus.COMPLETED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        purchaseOrderRepository.save(po);
    }

    private String generateGrnNumber() {
        String prefix = "GRN-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "-";
        long count = grnRepository.count() + 1;
        return prefix + String.format("%04d", count);
    }

    private GRNResponseDTO toResponseDTO(GoodsReceiptNote grn) {
        GRNResponseDTO dto = new GRNResponseDTO();
        dto.setId(grn.getId());
        dto.setGrnNumber(grn.getGrnNumber());
        dto.setGrnDate(grn.getGrnDate());
        dto.setWarehouse(grn.getWarehouse());
        dto.setStatus(grn.getStatus() != null ? grn.getStatus().name() : null);
        dto.setTotalAmount(grn.getTotalAmount());
        dto.setRemarks(grn.getRemarks());
        dto.setCreatedBy(grn.getCreatedBy());
        dto.setCreatedDate(grn.getCreatedDate());

        if (grn.getPurchaseOrder() != null) {
            dto.setPurchaseOrderId(grn.getPurchaseOrder().getId());
            dto.setPurchaseOrderNumber(grn.getPurchaseOrder().getPurchaseOrderNumber());
        }
        if (grn.getVendor() != null) {
            dto.setVendorId((long) grn.getVendor().getId());
            dto.setVendorName(grn.getVendor().getCompanyName());
        }

        if (grn.getItems() != null) {
            dto.setItems(grn.getItems().stream().map(this::toLineDTO).collect(Collectors.toList()));
        }
        return dto;
    }

    private GRNLineItemDTO toLineDTO(GoodsReceiptItem item) {
        GRNLineItemDTO dto = new GRNLineItemDTO();
        dto.setId(item.getId());
        dto.setOrderedQty(item.getOrderedQty());
        dto.setReceivedQty(item.getReceivedQty());
        dto.setAcceptedQty(item.getAcceptedQty());
        dto.setRejectedQty(item.getRejectedQty());
        dto.setRate(item.getRate());
        dto.setAmount(item.getAmount());
        dto.setBatchNo(item.getBatchNo());
        dto.setExpiryDate(item.getExpiryDate());
        dto.setRejectionReason(item.getRejectionReason());
        if (item.getItem() != null) {
            dto.setInventoryItemId(item.getItem().getInventoryItemId());
            dto.setItemCode(item.getItem().getItemCode());
            dto.setItemName(item.getItem().getName());
            dto.setUom(item.getItem().getUom() != null ? item.getItem().getUom().name() : null);
        }
        return dto;
    }
}
