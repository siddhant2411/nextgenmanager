package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstanceStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryLedger;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
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

    // ─── RESERVE ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void reserveStock(InventoryTransactionDTO req) {
        InventoryItem item = loadItem(req.getInventoryItemId());
        ProductInventorySettings settings = item.getProductInventorySettings();
        double qty = req.getQuantity();

        boolean isTracked = settings.isBatchTracked() || settings.isSerialTracked();

        if (settings.getAvailableQuantity() < qty && isTracked) {
            throw new RuntimeException("Insufficient available stock to reserve " + qty + " of " + item.getItemCode()
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
            throw new RuntimeException("Cannot consume " + qty + " of " + item.getItemCode()
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

        if (settings.isBatchTracked() || settings.isSerialTracked()) {
            int instanceCount = settings.isSerialTracked() ? (int) qty : 1;
            double qtyPerInstance = settings.isSerialTracked() ? 1.0 : qty;
            BigDecimal cost = BigDecimal.valueOf(req.getCostPerUnit());
            for (int i = 0; i < instanceCount; i++) {
                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(item);
                inst.setEntryDate(new Date());
                inst.setQuantity(BigDecimal.valueOf(qtyPerInstance));
                inst.setCostPerUnit(cost);
                inst.setSellPricePerUnit(cost);
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                inventoryInstanceRepository.save(inst);
            }
        }

        settings.setAvailableQuantity(settings.getAvailableQuantity() + qty);
        writeLedger(req, item, qty, settings.getAvailableQuantity());
        inventoryItemRepository.save(item);
        logger.info("PRODUCE {} of {} | available={}", qty, item.getItemCode(), settings.getAvailableQuantity());
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
        if (item == null) throw new RuntimeException("Inventory item not found: id=" + itemId);
        if (item.getProductInventorySettings() == null)
            throw new RuntimeException("Item " + item.getItemCode() + " has no inventory settings configured");
        return item;
    }

    private void writeLedger(InventoryTransactionDTO req, InventoryItem item, double movement, double closingBalance) {
        InventoryLedger ledger = new InventoryLedger();
        ledger.setMovementDate(LocalDate.now());
        ledger.setTransactionType(req.getTransactionType());
        ledger.setQuantity(movement);
        ledger.setRate(req.getCostPerUnit());
        ledger.setAmount(Math.abs(movement) * req.getCostPerUnit());
        ledger.setValuationMethod("AVERAGE");
        ledger.setWarehouse(req.getWarehouse());
        ledger.setReferenceType(req.getReferenceType());
        ledger.setReferenceDocNo(req.getReferenceDocNo());
        ledger.setCreatedBy(req.getCreatedBy());
        ledger.setScrappedQuantity(req.getScrappedQuantity());
        ledger.setOverrideReason(req.getOverrideReason());
        ledger.setClosingBalance(closingBalance);
        ledger.setInventoryItem(item);
        inventoryLedgerRepository.save(ledger);
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
