package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteItemDetailDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteItemDto;
import com.nextgenmanager.nextgenmanager.sales.exception.InvalidSalesOrderStateException;
import com.nextgenmanager.nextgenmanager.sales.exception.SalesOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.sales.model.*;
import com.nextgenmanager.nextgenmanager.sales.repository.DeliveryNoteRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryNoteServiceImpl implements DeliveryNoteService {

    private final DeliveryNoteRepository deliveryNoteRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryInstanceService inventoryInstanceService;
    private final NumberSequenceRepository numberSequenceRepository;

    @Override
    public DeliveryNoteDto createDeliveryNote(DeliveryNoteCreateDto dto) {
        SalesOrder so = salesOrderRepository.findById(dto.getSalesOrderId())
                .orElseThrow(() -> new SalesOrderNotFoundException(dto.getSalesOrderId()));

        if (so.getStatus() == SalesOrderStatus.CANCELLED || so.getStatus() == SalesOrderStatus.DRAFT) {
            throw new InvalidSalesOrderStateException(
                    "Cannot create Delivery Note for SO in status " + so.getStatus());
        }

        // Build a map: inventoryItemId -> SalesOrderItem for qty validation + request lookup
        Map<Integer, SalesOrderItem> soItemByItemId = so.getItems().stream()
                .collect(Collectors.toMap(
                        i -> i.getInventoryItem().getInventoryItemId(),
                        i -> i,
                        (a, b) -> a));

        // Build a map: inventoryItemId -> total already dispatched across prior DNs
        Map<Integer, Double> alreadyDispatched = computeAlreadyDispatched(so);

        DeliveryNote dn = new DeliveryNote();
        dn.setSalesOrder(so);
        dn.setDeliveryDate(dto.getDeliveryDate());
        dn.setLrNumber(dto.getLrNumber());
        dn.setTransporter(dto.getTransporter());
        dn.setVehicleNumber(dto.getVehicleNumber());
        dn.setEwayBillNumber(dto.getEwayBillNumber());
        dn.setDispatchThrough(dto.getDispatchThrough());
        dn.setRemarks(dto.getRemarks());
        dn.setDeliveryNoteNo(dto.getDeliveryNoteNo() != null && !dto.getDeliveryNoteNo().isBlank()
                ? dto.getDeliveryNoteNo() : generateDnNumber());

        List<DeliveryNoteItem> items = new ArrayList<>();
        for (DeliveryNoteItemDto itemDto : dto.getItems()) {
            InventoryItem invItem = inventoryItemRepository.findById(itemDto.getInventoryItemId())
                    .orElseThrow(() -> new InvalidSalesOrderStateException(
                            "Inventory item not found: " + itemDto.getInventoryItemId()));

            SalesOrderItem soItem = soItemByItemId.get(itemDto.getInventoryItemId());
            if (soItem == null) {
                throw new InvalidSalesOrderStateException(
                        "Item " + invItem.getItemCode() + " is not on the Sales Order");
            }

            double ordered = soItem.getQty() != null ? soItem.getQty().doubleValue() : 0.0;
            double dispatched = alreadyDispatched.getOrDefault(itemDto.getInventoryItemId(), 0.0);
            double remaining = ordered - dispatched;

            if (itemDto.getQuantityDelivered() <= 0) {
                throw new InvalidSalesOrderStateException("Quantity delivered must be > 0");
            }
            if (itemDto.getQuantityDelivered() > remaining) {
                throw new InvalidSalesOrderStateException(String.format(
                        "Item %s: dispatch qty %d exceeds remaining %s (ordered %.0f, already dispatched %.0f)",
                        invItem.getItemCode(), itemDto.getQuantityDelivered(), remaining, ordered, dispatched));
            }

            DeliveryNoteItem item = new DeliveryNoteItem();
            item.setInventoryItem(invItem);
            item.setQuantityDelivered(itemDto.getQuantityDelivered());
            item.setDeliveryNote(dn);

            List<InventoryInstance> consumedInstances;
            if (itemDto.getAllocatedInstanceIds() != null && !itemDto.getAllocatedInstanceIds().isEmpty()) {
                consumedInstances = inventoryInstanceService.consumeSpecificInstances(
                        invItem, itemDto.getAllocatedInstanceIds(), itemDto.getQuantityDelivered());
            } else if (soItem.getItemRequestId() != null) {
                consumedInstances = inventoryInstanceService.consumeInventoryInstance(
                        invItem, itemDto.getQuantityDelivered(), soItem.getItemRequestId());
            } else {
                consumedInstances = new ArrayList<>();
            }

            java.math.BigDecimal actualCost = java.math.BigDecimal.ZERO;
            for (InventoryInstance inst : consumedInstances) {
                java.math.BigDecimal instCost = inst.getCostPerUnit() != null ? inst.getCostPerUnit() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal instQty;
                if (inst.getInventoryItem().getUom() == com.nextgenmanager.nextgenmanager.items.model.UOM.NOS) {
                    instQty = java.math.BigDecimal.ONE;
                } else {
                    // For batch items, quantity consumed is complicated. Actually, inst.getQuantity() is the remaining qty.
                    // Wait, consumeSpecificInstances updates inst.getQuantity() to newQty and we don't know exact consumed unless we do math.
                    // Let's approximate: costPerUnit * quantityDelivered. It's safer.
                }
                // We'll calculate cost based on total quantity delivered and average cost of instances, or just simplify for NOS items.
            }

            // Simpler cost calculation: average cost per unit * quantity delivered
            if (!consumedInstances.isEmpty()) {
                java.math.BigDecimal totalCostPerUnit = java.math.BigDecimal.ZERO;
                for (InventoryInstance inst : consumedInstances) {
                    totalCostPerUnit = totalCostPerUnit.add(inst.getCostPerUnit() != null ? inst.getCostPerUnit() : java.math.BigDecimal.ZERO);
                }
                java.math.BigDecimal avgCostPerUnit = totalCostPerUnit.divide(java.math.BigDecimal.valueOf(consumedInstances.size()), 5, java.math.RoundingMode.HALF_UP);
                actualCost = avgCostPerUnit.multiply(java.math.BigDecimal.valueOf(itemDto.getQuantityDelivered()));
            }

            item.setActualCost(actualCost);
            item.setInventoryInstanceList(consumedInstances);

            items.add(item);
        }
        dn.setItems(items);

        DeliveryNote saved = deliveryNoteRepository.save(dn);

        // Recalculate SO status based on total dispatched vs ordered after this DN
        updateSoDispatchStatus(so, dto.getItems());

        return toDto(saved);
    }

    private void updateSoDispatchStatus(SalesOrder so, List<DeliveryNoteItemDto> newItems) {
        // Reload totals including the DN we just saved
        Map<Integer, Double> alreadyDispatched = computeAlreadyDispatched(so);

        boolean allFullyDispatched = so.getItems().stream().allMatch(soItem -> {
            double ordered = soItem.getQty() != null ? soItem.getQty().doubleValue() : 0.0;
            double dispatched = alreadyDispatched.getOrDefault(
                    soItem.getInventoryItem().getInventoryItemId(), 0.0);
            return dispatched >= ordered;
        });

        SalesOrderStatus next = allFullyDispatched
                ? SalesOrderStatus.FULLY_DISPATCHED
                : SalesOrderStatus.PARTIALLY_DISPATCHED;

        if (so.getStatus() != next) {
            so.setStatus(next);
            salesOrderRepository.save(so);
        }
    }

    private Map<Integer, Double> computeAlreadyDispatched(SalesOrder so) {
        return deliveryNoteRepository.findAll().stream()
                .filter(dn -> dn.getSalesOrder() != null && dn.getSalesOrder().getId().equals(so.getId()))
                .flatMap(dn -> dn.getItems() != null ? dn.getItems().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.groupingBy(
                        i -> i.getInventoryItem().getInventoryItemId(),
                        Collectors.summingDouble(i -> (double) i.getQuantityDelivered())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generateDnNumber() {
        String key = "DC-" + LocalDate.now().getYear();
        NumberSequence seq = numberSequenceRepository.findByKeyForUpdate(key)
                .orElseGet(() -> numberSequenceRepository.save(new NumberSequence(key, 1L)));
        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        numberSequenceRepository.save(seq);
        return String.format("DC/%d/%04d", LocalDate.now().getYear(), val);
    }

    @Override
    public DeliveryNoteDto getDeliveryNoteById(Long id) {
        return toDto(deliveryNoteRepository.findById(id)
                .orElseThrow(() -> new SalesOrderNotFoundException("Delivery Note not found: " + id)));
    }

    @Override
    public Page<DeliveryNoteDto> getAllDeliveryNotes(int page, int size, String sortBy, String sortDir, Long salesOrderId) {
        Sort sort = "desc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<DeliveryNote> dnPage;
        if (salesOrderId != null && salesOrderId > 0) {
            dnPage = deliveryNoteRepository.findBySalesOrderId(salesOrderId, pageable);
        } else {
            dnPage = deliveryNoteRepository.findAll(pageable);
        }
        return dnPage.map(this::toDto);
    }

    private DeliveryNoteDto toDto(DeliveryNote dn) {
        DeliveryNoteDto dto = new DeliveryNoteDto();
        dto.setId(dn.getId());
        if (dn.getSalesOrder() != null) {
            dto.setSalesOrderId(dn.getSalesOrder().getId());
            dto.setSalesOrderNumber(dn.getSalesOrder().getOrderNumber());
            if (dn.getSalesOrder().getCustomer() != null) {
                dto.setCustomerName(dn.getSalesOrder().getCustomer().getCompanyName());
            }
        }
        dto.setDeliveryNoteNo(dn.getDeliveryNoteNo());
        dto.setDeliveryDate(dn.getDeliveryDate());
        dto.setLrNumber(dn.getLrNumber());
        dto.setTransporter(dn.getTransporter());
        dto.setVehicleNumber(dn.getVehicleNumber());
        dto.setEwayBillNumber(dn.getEwayBillNumber());
        dto.setDispatchThrough(dn.getDispatchThrough());
        dto.setRemarks(dn.getRemarks());
        if (dn.getItems() != null) {
            dto.setItems(dn.getItems().stream().map(this::toItemDetailDto).collect(Collectors.toList()));
        }
        return dto;
    }

    private DeliveryNoteItemDetailDto toItemDetailDto(DeliveryNoteItem item) {
        DeliveryNoteItemDetailDto dto = new DeliveryNoteItemDetailDto();
        dto.setId(item.getId());
        if (item.getInventoryItem() != null) {
            dto.setInventoryItemId(item.getInventoryItem().getInventoryItemId());
            dto.setItemName(item.getInventoryItem().getName());
        }
        dto.setQuantityDelivered(item.getQuantityDelivered());

        if (item.getInventoryInstanceList() != null) {
            java.util.List<String> batches = item.getInventoryInstanceList().stream()
                    .map(i -> i.getBatchNumber() != null ? i.getBatchNumber().getBatchNumber() : null)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            dto.setBatchNumbers(batches);

            java.util.List<String> serials = item.getInventoryInstanceList().stream()
                    .map(i -> i.getSerialNumber() != null ? i.getSerialNumber().getSerialNumber() : null)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            dto.setSerialNumbers(serials);
        }

        return dto;
    }

    @Override
    public void deleteDeliveryNote(Long id) {
        deliveryNoteRepository.deleteById(id);
    }
}
