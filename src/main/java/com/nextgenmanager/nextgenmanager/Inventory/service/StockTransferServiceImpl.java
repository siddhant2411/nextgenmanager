package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferCreateRequest;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferLineDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockTransferReceiveRequest;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.ItemWarehouseStockRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.StockTransferRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Stock transfers between warehouses.
 *
 * <p>Company-wide counters are deliberately untouched throughout: a transfer moves stock, it does
 * not create or destroy it. What moves is the split — out of the source, into transit, then into
 * the destination — so that
 * {@code availableQuantity == SUM(onHand) + SUM(inTransit)} holds at every step, including while
 * goods are on a vehicle.
 *
 * <p>No ledger row is written. The ledger's {@code closingBalance} is a company-wide running
 * balance that a transfer does not change, and rows whose balance never moves would add noise
 * without information. The transfer document itself is the audit trail for the movement.
 */
@Service
@RequiredArgsConstructor
public class StockTransferServiceImpl implements StockTransferService {

    private static final Logger logger = LoggerFactory.getLogger(StockTransferServiceImpl.class);

    private final StockTransferRepository stockTransferRepository;
    private final ItemWarehouseStockRepository itemWarehouseStockRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final WarehouseService warehouseService;
    private final StockTransferNumberGenerator numberGenerator;

    // ─── Reads ────────────────────────────────────────────────────────────────

    @Override
    public List<StockTransferDto> list(StockTransferStatus status, Long warehouseId) {
        List<StockTransfer> rows;
        if (status != null)           rows = stockTransferRepository.findLiveByStatus(status);
        else if (warehouseId != null) rows = stockTransferRepository.findLiveByWarehouse(warehouseId);
        else                          rows = stockTransferRepository.findAllLive();
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    public StockTransferDto get(Long id) {
        return toDto(load(id));
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public StockTransferDto create(StockTransferCreateRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("A transfer needs at least one line");
        }

        Warehouse from = warehouseService.resolveByCodeOrDefault(request.fromWarehouseCode());
        Warehouse to   = warehouseService.resolveByCodeOrDefault(request.toWarehouseCode());
        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException(
                    "Source and destination are the same warehouse (" + from.getCode() + ")");
        }

        // Every line is resolved and validated BEFORE a number is drawn. The generator runs in its
        // own transaction, so a number it hands out is spent even when this one rolls back — asking
        // for it first meant a rejected request left a permanent gap in the sequence.
        List<StockTransferLine> lines = new ArrayList<>();
        for (StockTransferCreateRequest.Line l : request.lines()) {
            if (l.quantity() == null || l.quantity().signum() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero on every line");
            }
            InventoryItem item = inventoryItemRepository.findByActiveId(l.inventoryItemId());
            if (item == null) {
                throw new IllegalArgumentException("Inventory item not found: " + l.inventoryItemId());
            }
            StockTransferLine line = new StockTransferLine();
            line.setInventoryItem(item);
            line.setQuantity(l.quantity());
            line.setReceivedQuantity(BigDecimal.ZERO);
            line.setRemarks(l.remarks());
            lines.add(line);
        }

        StockTransfer transfer = new StockTransfer();
        transfer.setTransferNumber(numberGenerator.next());
        transfer.setFromWarehouse(from);
        transfer.setToWarehouse(to);
        transfer.setStatus(StockTransferStatus.DRAFT);
        transfer.setRemarks(request.remarks());
        transfer.setCreatedBy(currentUser());
        for (StockTransferLine line : lines) {
            line.setStockTransfer(transfer);
            transfer.getLines().add(line);
        }

        StockTransfer saved = stockTransferRepository.save(transfer);
        logger.info("Stock transfer {} created: {} -> {} ({} lines)",
                saved.getTransferNumber(), from.getCode(), to.getCode(), saved.getLines().size());
        return toDto(saved);
    }

    @Override
    @Transactional
    public StockTransferDto dispatch(Long id) {
        StockTransfer t = load(id);
        requireStatus(t, StockTransferStatus.DRAFT, "dispatch");

        // Every line is checked before any counter moves, so a transfer never half-dispatches.
        for (StockTransferLine line : t.getLines()) {
            BigDecimal available = onHandAt(line.getInventoryItem(), t.getFromWarehouse());
            if (available.compareTo(line.getQuantity()) < 0) {
                throw new IllegalStateException(String.format(
                        "Cannot dispatch %s of %s from %s: only %s on hand there",
                        line.getQuantity().toPlainString(),
                        line.getInventoryItem().getItemCode(),
                        t.getFromWarehouse().getCode(),
                        available.toPlainString()));
            }
        }

        for (StockTransferLine line : t.getLines()) {
            adjust(line.getInventoryItem(), t.getFromWarehouse(),
                    line.getQuantity().negate(), line.getQuantity());
        }

        t.setStatus(StockTransferStatus.DISPATCHED);
        t.setDispatchedDate(new Date());
        t.setUpdatedDate(new Date());
        logger.info("Stock transfer {} dispatched from {}", t.getTransferNumber(), t.getFromWarehouse().getCode());
        return toDto(stockTransferRepository.save(t));
    }

    @Override
    @Transactional
    public StockTransferDto receive(Long id, StockTransferReceiveRequest request) {
        StockTransfer t = load(id);
        requireStatus(t, StockTransferStatus.DISPATCHED, "receive");

        for (StockTransferLine line : t.getLines()) {
            BigDecimal received = requestedFor(request, line);

            if (received.signum() < 0 || received.compareTo(line.getQuantity()) > 0) {
                throw new IllegalArgumentException(String.format(
                        "Received quantity for %s must be between 0 and the dispatched %s",
                        line.getInventoryItem().getItemCode(), line.getQuantity().toPlainString()));
            }

            // The shortfall stays in transit at the source rather than balancing itself away:
            // stock that left and never arrived is something to investigate, not to round off.
            adjust(line.getInventoryItem(), t.getFromWarehouse(), BigDecimal.ZERO, received.negate());
            adjust(line.getInventoryItem(), t.getToWarehouse(), received, BigDecimal.ZERO);

            line.setReceivedQuantity(received);
        }

        t.setStatus(StockTransferStatus.RECEIVED);
        t.setReceivedDate(new Date());
        t.setUpdatedDate(new Date());
        if (request != null && request.remarks() != null && !request.remarks().isBlank()) {
            t.setRemarks(request.remarks());
        }
        logger.info("Stock transfer {} received at {}", t.getTransferNumber(), t.getToWarehouse().getCode());
        return toDto(stockTransferRepository.save(t));
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        StockTransfer t = load(id);
        if (t.getStatus() != StockTransferStatus.DRAFT) {
            String why = switch (t.getStatus()) {
                case CANCELLED -> "it is already cancelled";
                case DISPATCHED -> "stock has already left " + t.getFromWarehouse().getCode()
                        + " — receive it, or record the shortfall";
                case RECEIVED -> "stock has already arrived at " + t.getToWarehouse().getCode();
                default -> "it is " + t.getStatus();
            };
            throw new IllegalStateException(
                    "Cannot cancel " + t.getTransferNumber() + ": " + why + ".");
        }
        t.setStatus(StockTransferStatus.CANCELLED);
        t.setUpdatedDate(new Date());
        stockTransferRepository.save(t);
        logger.info("Stock transfer {} cancelled", t.getTransferNumber());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private StockTransfer load(Long id) {
        return stockTransferRepository.findLiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stock transfer not found: " + id));
    }

    private void requireStatus(StockTransfer t, StockTransferStatus expected, String action) {
        if (t.getStatus() != expected) {
            throw new IllegalStateException(String.format(
                    "Cannot %s %s: it is %s, not %s", action, t.getTransferNumber(), t.getStatus(), expected));
        }
    }

    /** A line the caller did not name is received in full. */
    private BigDecimal requestedFor(StockTransferReceiveRequest request, StockTransferLine line) {
        if (request == null || request.lines() == null) return line.getQuantity();
        return request.lines().stream()
                .filter(l -> l.lineId() != null && l.lineId().equals(line.getId()))
                .findFirst()
                .map(l -> l.receivedQuantity() != null ? l.receivedQuantity() : line.getQuantity())
                .orElse(line.getQuantity());
    }

    private BigDecimal onHandAt(InventoryItem item, Warehouse warehouse) {
        return itemWarehouseStockRepository.find(item.getInventoryItemId(), warehouse.getId())
                .map(ItemWarehouseStock::getOnHand)
                .orElse(BigDecimal.ZERO);
    }

    private void adjust(InventoryItem item, Warehouse warehouse,
                        BigDecimal onHandDelta, BigDecimal inTransitDelta) {
        ItemWarehouseStock row = itemWarehouseStockRepository
                .find(item.getInventoryItemId(), warehouse.getId())
                .orElseGet(() -> {
                    ItemWarehouseStock fresh = new ItemWarehouseStock();
                    fresh.setInventoryItem(item);
                    fresh.setWarehouse(warehouse);
                    return fresh;
                });
        row.setOnHand(row.getOnHand().add(onHandDelta));
        row.setInTransit(row.getInTransit().add(inTransitDelta));
        row.setUpdatedDate(new Date());
        itemWarehouseStockRepository.save(row);
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {
            return "system";
        }
    }

    private StockTransferDto toDto(StockTransfer t) {
        List<StockTransferLineDto> lines = new ArrayList<>();
        for (StockTransferLine l : t.getLines()) {
            lines.add(new StockTransferLineDto(
                    l.getId(),
                    l.getInventoryItem().getInventoryItemId(),
                    l.getInventoryItem().getItemCode(),
                    l.getInventoryItem().getName(),
                    l.getQuantity(),
                    l.getReceivedQuantity(),
                    l.getRemarks()));
        }
        return new StockTransferDto(
                t.getId(), t.getTransferNumber(),
                t.getFromWarehouse().getId(), t.getFromWarehouse().getCode(),
                t.getToWarehouse().getId(),   t.getToWarehouse().getCode(),
                t.getStatus(), t.getDispatchedDate(), t.getReceivedDate(),
                t.getRemarks(), t.getCreatedBy(), lines);
    }
}
