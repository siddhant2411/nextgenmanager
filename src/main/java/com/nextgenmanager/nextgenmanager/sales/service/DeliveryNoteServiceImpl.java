package com.nextgenmanager.nextgenmanager.sales.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.NumberSequence;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryInstanceService;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionService;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteCreateDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteItemDetailDto;
import com.nextgenmanager.nextgenmanager.sales.dto.DeliveryNoteItemDto;
import com.nextgenmanager.nextgenmanager.sales.exception.InsufficientStockForDeliveryException;
import com.nextgenmanager.nextgenmanager.sales.exception.InvalidSalesOrderStateException;
import com.nextgenmanager.nextgenmanager.sales.exception.SalesOrderNotFoundException;
import com.nextgenmanager.nextgenmanager.sales.model.*;
import com.nextgenmanager.nextgenmanager.sales.repository.DeliveryNoteRepository;
import com.nextgenmanager.nextgenmanager.sales.repository.SalesOrderRepository;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final InventoryTransactionService inventoryTransactionService;
    private final NumberSequenceRepository numberSequenceRepository;
    private final StoreInventoryRequestService storeInventoryRequestService;

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

        // BACKSTOP (primary path is route-aware reservation at SO approval — see
        // SalesOrderServiceImpl.reserveInventory). This catches the planning-failure cases that
        // slip through: legacy SOs approved before route-aware reservation, MTS items that came up
        // short, or direct DCs. For batch/serial tracked items with no pre-allocated instances and
        // insufficient available stock, raise a procurement need (deduped per SO+item) and block the DN.
        String currentUser = "system";
        try {
            currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) { }

        List<InsufficientStockForDeliveryException.ShortfallDetail> shortfalls = new ArrayList<>();
        for (DeliveryNoteItemDto itemDto : dto.getItems()) {
            InventoryItem invItem = inventoryItemRepository.findById(itemDto.getInventoryItemId())
                    .orElse(null);
            if (invItem == null) continue;

            ProductInventorySettings settings = invItem.getProductInventorySettings();
            if (settings == null) continue;
            if (!settings.isBatchTracked() && !settings.isSerialTracked()) continue;

            // Tracked item — skip if the caller already provided specific instance IDs
            boolean hasAllocatedInstances = itemDto.getAllocatedInstanceIds() != null
                    && !itemDto.getAllocatedInstanceIds().isEmpty();
            if (hasAllocatedInstances) continue;

            SalesOrderItem soItem = soItemByItemId.get(itemDto.getInventoryItemId());
            // Skip if a prior inventory request already exists for this SO line
            if (soItem != null && soItem.getItemRequestId() != null) continue;

            double available = settings.getAvailableQuantity();
            if (available < itemDto.getQuantityDelivered()) {
                InventoryRequest storeRequest = storeInventoryRequestService.createOrFetchStoreRequest(
                        invItem.getInventoryItemId(),
                        itemDto.getQuantityDelivered(),
                        dto.getSalesOrderId(),
                        soItem != null ? soItem.getId() : null,
                        currentUser);
                shortfalls.add(new InsufficientStockForDeliveryException.ShortfallDetail(
                        invItem.getItemCode(),
                        invItem.getName(),
                        itemDto.getQuantityDelivered(),
                        available,
                        storeRequest.getId(),
                        storeRequest.getReferenceNumber()));
            }
        }

        if (!shortfalls.isEmpty()) {
            throw new InsufficientStockForDeliveryException(shortfalls);
        }

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

            // DN number is set on the dn object before save, so it is available here
            String dnNo = dn.getDeliveryNoteNo();

            List<InventoryInstance> consumedInstances;
            if (itemDto.getAllocatedInstanceIds() != null && !itemDto.getAllocatedInstanceIds().isEmpty()) {
                consumedInstances = inventoryInstanceService.consumeSpecificInstances(
                        invItem, itemDto.getAllocatedInstanceIds(), itemDto.getQuantityDelivered(), dnNo);
            } else if (soItem.getItemRequestId() != null) {
                consumedInstances = inventoryInstanceService.consumeInventoryInstance(
                        invItem, (double) itemDto.getQuantityDelivered(), soItem.getItemRequestId(), dnNo);
            } else {
                ProductInventorySettings settings = invItem.getProductInventorySettings();
                boolean isTracked = settings != null && (settings.isBatchTracked() || settings.isSerialTracked());
                if (isTracked) {
                    throw new InvalidSalesOrderStateException(
                            "Item " + invItem.getItemCode() + " is batch/serial tracked. "
                            + "Please select specific batch/serial instances to dispatch.");
                }
                consumedInstances = new ArrayList<>();
            }

            // Cost of the goods leaving = average cost per unit of the consumed instances. Computed up
            // front so it values BOTH the dispatch ledger row (→ perpetual COGS posting) and the
            // DeliveryNoteItem.actualCost used for profitability reporting.
            java.math.BigDecimal avgCostPerUnit = java.math.BigDecimal.ZERO;
            if (!consumedInstances.isEmpty()) {
                java.math.BigDecimal totalCostPerUnit = java.math.BigDecimal.ZERO;
                for (InventoryInstance inst : consumedInstances) {
                    totalCostPerUnit = totalCostPerUnit.add(
                            inst.getCostPerUnit() != null ? inst.getCostPerUnit() : java.math.BigDecimal.ZERO);
                }
                avgCostPerUnit = totalCostPerUnit.divide(
                        java.math.BigDecimal.valueOf(consumedInstances.size()), 5, java.math.RoundingMode.HALF_UP);
            }
            java.math.BigDecimal actualCost =
                    avgCostPerUnit.multiply(java.math.BigDecimal.valueOf(itemDto.getQuantityDelivered()));

            // Write SALES_DISPATCH ledger entry so the Stock Ledger Report captures this outward movement
            // and accounting books COGS (Dr COGS / Cr Finished Goods) at the dispatched cost.
            // ProductInventorySettings.availableQuantity is already correct at this point because
            // consumeSpecificInstances / consumeInventoryInstance called updateItemAvailability().
            try {
                InventoryTransactionDTO dispatchDto = new InventoryTransactionDTO();
                dispatchDto.setInventoryItemId(invItem.getInventoryItemId());
                dispatchDto.setQuantity(itemDto.getQuantityDelivered());
                dispatchDto.setTransactionType("SALES_DISPATCH");
                dispatchDto.setReferenceType("DELIVERY_NOTE");
                dispatchDto.setReferenceDocNo(dnNo);
                dispatchDto.setCostPerUnit(avgCostPerUnit.doubleValue());
                // Also store the Sales Order number for cross-reference
                if (dn.getSalesOrder() != null) {
                    dispatchDto.setOverrideReason("SO: " + dn.getSalesOrder().getOrderNumber());
                }
                try {
                    dispatchDto.setCreatedBy(
                        org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication().getName());
                } catch (Exception ignored) { }
                inventoryTransactionService.writeDispatchLedger(dispatchDto);
            } catch (Exception e) {
                // Log but don't fail the entire DN — instance consumption already succeeded
                org.slf4j.LoggerFactory.getLogger(DeliveryNoteServiceImpl.class)
                    .error("Failed to write dispatch ledger for item {} on DN {}: {}",
                        invItem.getItemCode(), dnNo, e.getMessage());
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
