package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.*;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.BatchNumberRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.GoodsReceiptNoteRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.SerialNumberRepository;
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
    @Autowired private BatchNumberRepository batchNumberRepository;
    @Autowired private SerialNumberRepository serialNumberRepository;

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
            line.setBatchNo(lineDto.getSupplierBatchNo()); // store supplier batch on GRN line for reference
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
                // Batch / serial fields from request line
                txn.setSupplierBatchNo(lineDto.getSupplierBatchNo());
                txn.setManufacturingDate(lineDto.getManufacturingDate());
                txn.setExpiryDate(lineDto.getExpiryDate());
                txn.setManualSerialNumbers(lineDto.getManualSerialNumbers());
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
    @Transactional
    public GRNResponseDTO getGRN(Long grnId) {
        GoodsReceiptNote grn = grnRepository.findById(grnId)
                .orElseThrow(() -> new RuntimeException("GRN not found: " + grnId));
        return toResponseDTO(grn);
    }

    @Override
    @Transactional
    public List<GRNResponseDTO> getGRNsByPurchaseOrder(Long purchaseOrderId) {
        return grnRepository.findByPurchaseOrder_Id(purchaseOrderId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
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

        boolean allFulfilled = true;
        for (PurchaseOrderItem poItem : po.getItems()) {
            double totalReceived = allGrns.stream()
                    .flatMap(g -> g.getItems().stream())
                    .filter(gi -> gi.getItem().getInventoryItemId() == poItem.getItem().getInventoryItemId())
                    .mapToDouble(GoodsReceiptItem::getAcceptedQty)
                    .sum();
            poItem.setQuantityReceived(totalReceived);
            if (totalReceived < poItem.getQuantityOrdered()) allFulfilled = false;
        }

        po.setStatus(allFulfilled ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
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
        dto.setExpiryDate(item.getExpiryDate());
        dto.setSupplierBatchNo(item.getBatchNo()); // batchNo column stores supplier batch reference
        dto.setRejectionReason(item.getRejectionReason());
        if (item.getItem() != null) {
            dto.setInventoryItemId(item.getItem().getInventoryItemId());
            dto.setItemCode(item.getItem().getItemCode());
            dto.setItemName(item.getItem().getName());
            dto.setUom(item.getItem().getUom() != null ? item.getItem().getUom().name() : null);

            // Populate generated batch/serial numbers from inventory instances
            if (item.getItem().getProductInventorySettings() != null) {
                boolean isBatch  = item.getItem().getProductInventorySettings().isBatchTracked();
                boolean isSerial = item.getItem().getProductInventorySettings().isSerialTracked();
                String grnNo = item.getGoodsReceiptNote().getGrnNumber();
                int itemId   = item.getItem().getInventoryItemId();

                if (isBatch) {
                    batchNumberRepository.findBySourceDocNo(grnNo)
                            .stream()
                            .filter(b -> b.getInventoryItem().getInventoryItemId() == itemId)
                            .findFirst()
                            .ifPresent(b -> dto.setGeneratedBatchNumber(b.getBatchNumber()));
                }
                if (isSerial) {
                    List<String> serials = serialNumberRepository
                            .findBySourceDocNo(grnNo)
                            .stream()
                            .filter(s -> s.getInventoryItem().getInventoryItemId() == itemId)
                            .map(SerialNumber::getSerialNumber)
                            .collect(Collectors.toList());
                    dto.setGeneratedSerialNumbers(serials);
                }
            }
        }
        return dto;
    }
}
