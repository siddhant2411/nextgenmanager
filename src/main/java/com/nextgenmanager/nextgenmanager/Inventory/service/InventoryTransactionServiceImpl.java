package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.events.InventoryMovementPostedEvent;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class InventoryTransactionServiceImpl implements InventoryTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryTransactionServiceImpl.class);

    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private InventoryLedgerRepository inventoryLedgerRepository;
    @Autowired private InventoryInstanceRepository inventoryInstanceRepository;
    @Autowired private BatchSerialService batchSerialService;
    @Autowired private DomainEventPublisher eventPublisher;

    // ─── RESERVE ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void reserveStock(InventoryTransactionDTO req) {
        InventoryItem item = loadItem(req.getInventoryItemId());
        ProductInventorySettings settings = item.getProductInventorySettings();
        double qty = req.getQuantity();

        boolean isTracked = settings.isBatchTracked() || settings.isSerialTracked();

        if (settings.getAvailableQuantity() < qty && isTracked) {
            throw new IllegalStateException("Insufficient stock to reserve " + qty + " of " + item.getItemCode()
                + ". Available: " + settings.getAvailableQuantity());
        }

        if (isTracked) {
            List<InventoryInstance> toReserve = inventoryInstanceRepository
                    .findByItemAndStatusFIFO(item.getInventoryItemId(), InventoryInstanceStatus.AVAILABLE.name());
            double remaining = qty;
            for (InventoryInstance inst : toReserve) {
                if (remaining <= 0) break;
                double instQty = inst.getQuantity().doubleValue();
                double take = Math.min(instQty, remaining);
                if (take < instQty) {
                    inst.setQuantity(BigDecimal.valueOf(instQty - take));
                    // Split remainder stays AVAILABLE; create a new REQUESTED instance for the taken portion
                    InventoryInstance reserved = new InventoryInstance();
                    reserved.setInventoryItem(item);
                    reserved.setEntryDate(inst.getEntryDate());
                    reserved.setQuantity(BigDecimal.valueOf(take));
                    reserved.setCostPerUnit(inst.getCostPerUnit());
                    reserved.setSellPricePerUnit(inst.getSellPricePerUnit());
                    reserved.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                    reserved.setBookedDate(new Date());
                    inventoryInstanceRepository.save(reserved);
                } else {
                    inst.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                    inst.setBookedDate(new Date());
                }
                inventoryInstanceRepository.save(inst);
                remaining -= take;
            }
            if (remaining > 0) {
                // Counter check already passed — instance records are missing (legacy stock or direct adjustment).
                // Backfill a synthetic AVAILABLE instance so the reservation can proceed.
                logger.warn("No tracked instances found for {} but counter shows sufficient stock. "
                        + "Backfilling {} units as a synthetic instance.", item.getItemCode(), remaining);
                InventoryInstance backfill = new InventoryInstance();
                backfill.setInventoryItem(item);
                backfill.setEntryDate(new Date());
                backfill.setQuantity(BigDecimal.valueOf(remaining));
                backfill.setCostPerUnit(BigDecimal.ZERO);
                backfill.setSellPricePerUnit(BigDecimal.ZERO);
                backfill.setInventoryInstanceStatus(InventoryInstanceStatus.REQUESTED);
                backfill.setBookedDate(new Date());
                inventoryInstanceRepository.save(backfill);
            }
        }

        settings.setAvailableQuantity(settings.getAvailableQuantity() - qty);
        settings.setReservedQuantity(settings.getReservedQuantity() + qty);
        writeLedger(req, item, -qty, settings.getAvailableQuantity());
        inventoryItemRepository.save(item);
        logger.info("RESERVE {} of {} | available={} reserved={}", qty, item.getItemCode(),
                settings.getAvailableQuantity(), settings.getReservedQuantity());
    }

    // ─── ISSUE (tracking only) ────────────────────────────────────────────────

    @Override
    @Transactional
    public void issueStock(InventoryTransactionDTO req) {
        // Physical move to shop floor — no balance change.
        // Stock was already removed from availableQty at RESERVE.
        InventoryItem item = loadItem(req.getInventoryItemId());
        ProductInventorySettings settings = item.getProductInventorySettings();
        writeLedger(req, item, 0, settings.getAvailableQuantity()); // qty=0: balance unchanged
        logger.info("ISSUE (tracking) {} of {} | balances unchanged", req.getQuantity(), item.getItemCode());
    }

    // ─── CONSUME ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void consumeStock(InventoryTransactionDTO req) {
        InventoryItem item = loadItem(req.getInventoryItemId());
        ProductInventorySettings settings = item.getProductInventorySettings();
        double qty = req.getQuantity();

        boolean isTracked = settings.isBatchTracked() || settings.isSerialTracked();
        double totalReachable = settings.getReservedQuantity() + settings.getAvailableQuantity();
        if (totalReachable < qty && isTracked) {
            throw new IllegalStateException("Cannot consume " + qty + " of " + item.getItemCode()
                + ". Reserved: " + settings.getReservedQuantity()
                + ", Available: " + settings.getAvailableQuantity());
        }

        if (isTracked) {
            consumeTrackedInstances(item, qty, req.getReferenceDocNo(),
                    req.getOverrideInstanceIds(), req.getOverrideReason());
        }

        // Deduct from reservedQty first; overflow (unplanned consumption) comes from availableQty
        double fromReserved = Math.min(qty, settings.getReservedQuantity());
        double fromAvailable = qty - fromReserved;
        settings.setReservedQuantity(settings.getReservedQuantity() - fromReserved);
        settings.setAvailableQuantity(settings.getAvailableQuantity() - fromAvailable);

        writeLedger(req, item, -qty, settings.getAvailableQuantity());
        inventoryItemRepository.save(item);
        logger.info("CONSUME {} of {} | available={} reserved={}", qty, item.getItemCode(),
                settings.getAvailableQuantity(), settings.getReservedQuantity());
    }

    // ─── PRODUCE ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void produceStock(InventoryTransactionDTO req) {
        InventoryItem item = loadItem(req.getInventoryItemId());
        ProductInventorySettings settings = item.getProductInventorySettings();
        double qty = req.getQuantity();
        boolean isBatch  = settings.isBatchTracked();
        boolean isSerial = settings.isSerialTracked();

        if (isBatch || isSerial) {
            // Derive the source label for batch/serial records:
            //   GRN transaction        → "GRN"
            //   WO completion (PRODUCE with referenceType=WORK_ORDER) → "WORK_ORDER"
            //   Everything else        → "MANUAL"
            String batchSource = "GRN".equals(req.getTransactionType()) ? "GRN"
                    : "WORK_ORDER".equals(req.getReferenceType()) ? "WORK_ORDER"
                    : "MANUAL";

            // ── Create batch record (one per produce call) ─────────────────
            BatchNumber batch = null;
            if (isBatch) {
                batch = batchSerialService.createBatch(
                        item, qty,
                        batchSource,
                        req.getReferenceDocNo(),
                        req.getWarehouse(),
                        req.getManufacturingDate(),
                        req.getExpiryDate(),
                        req.getSupplierBatchNo(),
                        req.getCreatedBy()
                );
            }

            // ── Create serial records (one per unit) ───────────────────────
            List<SerialNumber> serials = null;
            int instanceCount   = isSerial ? (int) qty : 1;
            double qtyPerInst   = isSerial ? 1.0 : qty;

            if (isSerial) {
                serials = batchSerialService.createSerials(
                        item, instanceCount, batch,
                        batchSource,
                        req.getReferenceDocNo(),
                        req.getWarehouse(),
                        req.getManualSerialNumbers(),
                        req.getCreatedBy()
                );
            }

            // ── Create InventoryInstance records ───────────────────────────
            BigDecimal cost = BigDecimal.valueOf(req.getCostPerUnit());
            if (cost.compareTo(BigDecimal.ZERO) <= 0 && item.getProductFinanceSettings() != null && item.getProductFinanceSettings().getStandardCost() != null) {
                cost = BigDecimal.valueOf(item.getProductFinanceSettings().getStandardCost());
            }

            for (int i = 0; i < instanceCount; i++) {
                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(item);
                inst.setEntryDate(new Date());
                inst.setQuantity(BigDecimal.valueOf(qtyPerInst));
                inst.setCostPerUnit(cost);
                inst.setSellPricePerUnit(cost);
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                inst.setBatchNumber(batch);
                if (serials != null) inst.setSerialNumber(serials.get(i));
                inventoryInstanceRepository.save(inst);
            }
        }

        settings.setAvailableQuantity(settings.getAvailableQuantity() + qty);
        writeLedger(req, item, qty, settings.getAvailableQuantity());
        inventoryItemRepository.save(item);
        logger.info("PRODUCE {} of {} | available={} | batch={} serial={}",
                qty, item.getItemCode(), settings.getAvailableQuantity(), isBatch, isSerial);
    }

    // ─── RETURN ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void returnStock(InventoryTransactionDTO req) {
        InventoryItem item = loadItem(req.getInventoryItemId());
        ProductInventorySettings settings = item.getProductInventorySettings();
        double qty = req.getQuantity();

        if (settings.isBatchTracked() || settings.isSerialTracked()) {
            // Return REQUESTED instances back to AVAILABLE (FIFO order of most-recently reserved)
            List<InventoryInstance> reserved = inventoryInstanceRepository
                    .findByItemAndStatusFIFO(item.getInventoryItemId(), InventoryInstanceStatus.REQUESTED.name());
            double remaining = qty;
            for (InventoryInstance inst : reserved) {
                if (remaining <= 0) break;
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                inst.setBookedDate(null);
                inventoryInstanceRepository.save(inst);
                remaining -= inst.getQuantity().doubleValue();
            }
        }

        settings.setAvailableQuantity(settings.getAvailableQuantity() + qty);
        double fromReserved = Math.min(qty, settings.getReservedQuantity());
        settings.setReservedQuantity(settings.getReservedQuantity() - fromReserved);
        writeLedger(req, item, qty, settings.getAvailableQuantity());
        inventoryItemRepository.save(item);
        logger.info("RETURN {} of {} | available={} reserved={}", qty, item.getItemCode(),
                settings.getAvailableQuantity(), settings.getReservedQuantity());
    }

    // ─── ADJUSTMENT ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void adjustStock(InventoryTransactionDTO req) {
        InventoryItem item = loadItem(req.getInventoryItemId());
        ProductInventorySettings settings = item.getProductInventorySettings();
        double delta = req.getQuantity(); // positive = add, negative = subtract
        settings.setAvailableQuantity(settings.getAvailableQuantity() + delta);
        writeLedger(req, item, delta, settings.getAvailableQuantity());
        inventoryItemRepository.save(item);
        logger.info("ADJUSTMENT {} of {} | available={}", delta, item.getItemCode(), settings.getAvailableQuantity());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    // ─── Query methods ────────────────────────────────────────────────────────

    @Override
    public List<InventoryLedger> getStockHistory(int itemId, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return inventoryLedgerRepository
                    .findByInventoryItem_InventoryItemIdAndMovementDateBetweenOrderByMovementDateDesc(itemId, from, to);
        }
        return inventoryLedgerRepository
                .findByInventoryItem_InventoryItemIdOrderByMovementDateDesc(itemId);
    }

    @Override
    public double getOpeningBalance(int itemId, LocalDate asOf) {
        List<InventoryLedger> latest = inventoryLedgerRepository
                .findLatestBeforeDate(itemId, asOf, PageRequest.of(0, 1));
        return latest.isEmpty() ? 0.0 : latest.get(0).getClosingBalance();
    }

    @Override
    public double getCurrentStock(int itemId, String warehouse) {
        List<InventoryLedger> latest = inventoryLedgerRepository
                .findLatestByItem(itemId, PageRequest.of(0, 1));
        return latest.isEmpty() ? 0.0 : latest.get(0).getClosingBalance();
    }

    @Override
    public double getStockValue(String warehouse) {
        return inventoryLedgerRepository.getStockValueByWarehouse(warehouse);
    }

    private InventoryItem loadItem(int itemId) {
        InventoryItem item = inventoryItemRepository.findByActiveId(itemId);
        if (item == null) throw new IllegalArgumentException("Inventory item not found: id=" + itemId);
        if (item.getProductInventorySettings() == null)
            throw new IllegalStateException("Item " + item.getItemCode() + " has no inventory settings configured");
        return item;
    }

    private void writeLedger(InventoryTransactionDTO req, InventoryItem item, double movement, double closingBalance) {
        double rate = effectiveRate(req, item);
        InventoryLedger ledger = new InventoryLedger();
        ledger.setMovementDate(LocalDate.now());
        ledger.setTransactionType(req.getTransactionType());
        ledger.setQuantity(movement);
        ledger.setRate(rate);
        ledger.setAmount(Math.abs(movement) * rate);
        ledger.setValuationMethod("AVERAGE");
        ledger.setWarehouse(req.getWarehouse());
        ledger.setReferenceType(req.getReferenceType());
        ledger.setReferenceDocNo(req.getReferenceDocNo());
        ledger.setCreatedBy(req.getCreatedBy());
        ledger.setScrappedQuantity(req.getScrappedQuantity());
        ledger.setOverrideReason(req.getOverrideReason());
        ledger.setClosingBalance(closingBalance);
        ledger.setInventoryItem(item);
        InventoryLedger saved = inventoryLedgerRepository.save(ledger);

        // Perpetual inventory (accounting Phase 3): notify accounting of every saved movement.
        // The accounting listener posts only value-changing types (GRN, consume, produce,
        // dispatch, adjustment, WO return) and ignores reserve / issue / zero-value rows.
        eventPublisher.publish(new InventoryMovementPostedEvent(saved.getId()));
    }

    /**
     * Unit cost used to value the ledger row. Callers that know the cost pass it (GRN rate,
     * WO produce realUnitCost, dispatch instance cost); when absent (e.g. WO material
     * consumption) fall back to the item's standard cost so the movement — and therefore the
     * perpetual-inventory GL posting — is never zero-valued.
     */
    private double effectiveRate(InventoryTransactionDTO req, InventoryItem item) {
        if (req.getCostPerUnit() > 0) return req.getCostPerUnit();
        ProductFinanceSettings finance = item.getProductFinanceSettings();
        if (finance != null && finance.getStandardCost() != null) {
            return finance.getStandardCost();
        }
        return req.getCostPerUnit();
    }

    // ─── SALES DISPATCH ───────────────────────────────────────────────────────

    /**
     * Writes a SALES_DISPATCH ledger entry for goods leaving via a Delivery Note.
     * Does NOT modify ProductInventorySettings — that was already done by
     * InventoryInstanceService.updateItemAvailability() when the instances were consumed.
     * This is a ledger-only write so the Stock Ledger Report captures the outward movement.
     */
    @Override
    @Transactional
    public void writeDispatchLedger(InventoryTransactionDTO req) {
        InventoryItem item = loadItem(req.getInventoryItemId());
        ProductInventorySettings settings = item.getProductInventorySettings();
        double qty = req.getQuantity();
        // closingBalance reflects the state AFTER instances were already consumed
        writeLedger(req, item, -qty, settings.getAvailableQuantity());
        logger.info("SALES_DISPATCH ledger: {} of {} | closing balance={} | ref={}",
                qty, item.getItemCode(), settings.getAvailableQuantity(), req.getReferenceDocNo());
    }

    private void consumeTrackedInstances(InventoryItem item, double qty, String refDocNo,
                                          List<Long> overrideIds, String overrideReason) {
        double remaining = qty;
        List<InventoryInstance> candidates;

        if (overrideIds != null && !overrideIds.isEmpty()) {
            candidates = inventoryInstanceRepository.findAllById(overrideIds);
        } else {
            // FIFO from REQUESTED pool (reserved for this order)
            candidates = inventoryInstanceRepository
                    .findByItemAndStatusFIFO(item.getInventoryItemId(), InventoryInstanceStatus.REQUESTED.name());
            // If still short, fall back to AVAILABLE (unplanned consumption)
            if (candidates.stream().mapToDouble(i -> i.getQuantity().doubleValue()).sum() < qty) {
                List<InventoryInstance> available = inventoryInstanceRepository
                        .findByItemAndStatusFIFO(item.getInventoryItemId(), InventoryInstanceStatus.AVAILABLE.name());
                candidates = new ArrayList<>(candidates);
                candidates.addAll(available);
            }
        }

        for (InventoryInstance inst : candidates) {
            if (remaining <= 0) break;
            if (inst.isConsumed() || inst.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

            double instQty = inst.getQuantity().doubleValue();
            double take = Math.min(instQty, remaining);

            inst.setQuantity(BigDecimal.valueOf(instQty - take));
            inst.setConsumptionReferenceNo(refDocNo);
            if (inst.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                inst.setConsumed(true);
                inst.setConsumeDate(new Date());
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.CONSUMED);
            }
            inventoryInstanceRepository.save(inst);
            remaining -= take;
        }

        if (remaining > 0) {
            logger.warn("Could not fully consume tracked instances for {}. Shortage: {}", item.getItemCode(), remaining);
        }
    }
}
