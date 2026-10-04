package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockReconciliationReportDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockScalarCorrectionRowDto;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockReconciliationRowDto;
import com.nextgenmanager.nextgenmanager.Inventory.events.InventoryMovementPostedEvent;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryLedgerRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.ItemWarehouseStockRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryTransactionServiceImpl implements InventoryTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryTransactionServiceImpl.class);

    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private InventoryLedgerRepository inventoryLedgerRepository;
    @Autowired private InventoryInstanceRepository inventoryInstanceRepository;
    @Autowired private BatchSerialService batchSerialService;
    @Autowired private DomainEventPublisher eventPublisher;
    @Autowired private WarehouseService warehouseService;
    @Autowired private ItemWarehouseStockRepository itemWarehouseStockRepository;
    @Autowired private com.nextgenmanager.nextgenmanager.Inventory.repository.WarehouseRepository warehouseRepository;

    // ─── RESERVE ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void reserveStock(InventoryTransactionDTO req) {
        InventoryItem item = loadItem(req.getInventoryItemId());
        ProductInventorySettings settings = item.getProductInventorySettings();
        double qty = req.getQuantity();

        boolean isTracked = settings.isBatchTracked() || settings.isSerialTracked();

        assertStockSufficient(item, settings, settings.getAvailableQuantity(), qty, "reserve");

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
                    // A split stays where its source sits — reserving does not move stock.
                    reserved.setWarehouse(inst.getWarehouse());
                    reserved.setStorageLocation(inst.getStorageLocation());
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
                // Synthetic row standing in for stock the counters claim but no instance records.
                backfill.setWarehouse(warehouseService.resolveDefaultWarehouse());
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
        applyWarehouseDelta(item, warehouseService.resolveByCodeOrDefault(req.getWarehouse()), -qty, qty);
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
        assertStockSufficient(item, settings, totalReachable, qty, "consume");

        if (isTracked) {
            consumeTrackedInstances(item, qty, req.getReferenceDocNo(),
                    req.getOverrideInstanceIds(), req.getOverrideReason());
        }

        // Deduct from reservedQty first; overflow (unplanned consumption) comes from availableQty
        double fromReserved = Math.min(qty, settings.getReservedQuantity());
        double fromAvailable = qty - fromReserved;
        settings.setReservedQuantity(settings.getReservedQuantity() - fromReserved);
        settings.setAvailableQuantity(settings.getAvailableQuantity() - fromAvailable);
        applyWarehouseDelta(item, warehouseService.resolveByCodeOrDefault(req.getWarehouse()),
                -fromAvailable, -fromReserved);

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

        // An untracked item normally gets no instance row at all — the scalars carry it alone,
        // and that is unchanged for an ordinary receipt. But a quality status other than PASSED
        // has nowhere to live except an instance, so a rejection asks for one explicitly. Without
        // this, rejected quantity on an untracked item was added to availableQuantity exactly like
        // good stock, with no record anywhere of which units were which — the failure this whole
        // step exists to stop.
        boolean explicitlyNotPassed = req.getQualityStatus() != null
                && req.getQualityStatus() != QualityStatus.PASSED;
        boolean createsInstance = isBatch || isSerial || explicitlyNotPassed;

        if (createsInstance) {
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

            // Resolved once rather than per unit: a production run can create many instances.
            Warehouse producedInto = warehouseService.resolveByCodeOrDefault(req.getWarehouse());
            for (int i = 0; i < instanceCount; i++) {
                InventoryInstance inst = new InventoryInstance();
                inst.setInventoryItem(item);
                inst.setWarehouse(producedInto);
                inst.setEntryDate(new Date());
                inst.setQuantity(BigDecimal.valueOf(qtyPerInst));
                inst.setCostPerUnit(cost);
                inst.setSellPricePerUnit(cost);
                inst.setInventoryInstanceStatus(InventoryInstanceStatus.AVAILABLE);
                // Rejected goods are received as FAILED so they are on the books where they
                // physically are, without being available to pick.
                inst.setQualityStatus(req.getQualityStatus() != null
                        ? req.getQualityStatus() : QualityStatus.PASSED);
                inst.setBatchNumber(batch);
                if (serials != null) inst.setSerialNumber(serials.get(i));
                inventoryInstanceRepository.save(inst);
            }
        }

        settings.setAvailableQuantity(settings.getAvailableQuantity() + qty);
        applyWarehouseDelta(item, warehouseService.resolveByCodeOrDefault(req.getWarehouse()), qty, 0);
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
        applyWarehouseDelta(item, warehouseService.resolveByCodeOrDefault(req.getWarehouse()), qty, -fromReserved);
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
        if (delta < 0) {
            assertStockSufficient(item, settings, settings.getAvailableQuantity(), -delta, "adjust down");
        }
        settings.setAvailableQuantity(settings.getAvailableQuantity() + delta);
        applyWarehouseDelta(item, warehouseService.resolveByCodeOrDefault(req.getWarehouse()), delta, 0);
        writeLedger(req, item, delta, settings.getAvailableQuantity());
        inventoryItemRepository.save(item);
        logger.info("ADJUSTMENT {} of {} | available={}", delta, item.getItemCode(), settings.getAvailableQuantity());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Tolerance for comparing double stock quantities. */
    private static final double STOCK_EPSILON = 1e-6;

    /**
     * Refuses a movement that would take an item below zero unless it is explicitly
     * configured to allow negative stock.
     *
     * <p>Previously {@code allowNegativeStock} was declared on ProductInventorySettings
     * and read nowhere in the codebase. The only quantity checks were written inline in
     * reserveStock and consumeStock and were gated on {@code isTracked}, so untracked
     * items bypassed them entirely — which is how items with the flag off reached
     * negative balances — and adjustStock had no check at all.
     *
     * <p>Tracked items can never opt out: a {@code @PreUpdate} hook on
     * ProductInventorySettings forces the flag to false whenever batch or serial
     * tracking is on.
     */
    private void assertStockSufficient(InventoryItem item, ProductInventorySettings settings,
                                       double reachable, double required, String operation) {
        if (settings.isAllowNegativeStock()) return;
        if (reachable - required >= -STOCK_EPSILON) return;

        throw new IllegalStateException(String.format(
                "Cannot %s %.4f of %s: only %.4f reachable (available %.4f, reserved %.4f) "
                        + "and this item is not configured to allow negative stock.",
                operation, required, item.getItemCode(), reachable,
                settings.getAvailableQuantity(), settings.getReservedQuantity()));
    }

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
        // Signature still takes a code so callers and the UI are unaffected by the FK change.
        List<InventoryLedger> latest = inventoryLedgerRepository
                .findLatestByItem(itemId, PageRequest.of(0, 1));
        return latest.isEmpty() ? 0.0 : latest.get(0).getClosingBalance();
    }

    @Override
    public double getStockValue(String warehouse) {
        // Null means every warehouse, so it must stay null rather than resolving to the default.
        Long warehouseId = (warehouse == null || warehouse.isBlank())
                ? null
                : warehouseService.resolveByCodeOrDefault(warehouse).getId();
        return inventoryLedgerRepository.getStockValueByWarehouse(warehouseId);
    }

    private InventoryItem loadItem(int itemId) {
        InventoryItem item = inventoryItemRepository.findByActiveId(itemId);
        if (item == null) throw new IllegalArgumentException("Inventory item not found: id=" + itemId);
        if (item.getProductInventorySettings() == null)
            throw new IllegalStateException("Item " + item.getItemCode() + " has no inventory settings configured");
        return item;
    }


    /**
     * Mirrors a movement into the per-warehouse counters.
     *
     * <p>Called with the same deltas already applied to the company-wide totals, so the invariant
     * the reconciliation report checks — company total equals the sum of the warehouse rows —
     * holds by construction rather than by hope. A row is created the first time stock touches a
     * warehouse; items at zero simply have none.
     */
    private void applyWarehouseDelta(InventoryItem item, Warehouse warehouse,
                                     double onHandDelta, double reservedDelta) {
        if (warehouse == null) return;

        ItemWarehouseStock row = itemWarehouseStockRepository
                .find(item.getInventoryItemId(), warehouse.getId())
                .orElseGet(() -> {
                    ItemWarehouseStock fresh = new ItemWarehouseStock();
                    fresh.setInventoryItem(item);
                    fresh.setWarehouse(warehouse);
                    return fresh;
                });

        row.setOnHand(row.getOnHand().add(BigDecimal.valueOf(onHandDelta)));
        row.setReserved(row.getReserved().add(BigDecimal.valueOf(reservedDelta)));
        row.setUpdatedDate(new Date());
        itemWarehouseStockRepository.save(row);
    }

    /**
     * Rewrites an item's per-warehouse counters from the warehouses its instances name.
     *
     * <p>The counters are normally kept by deltas, one movement at a time, which is right until
     * something has already gone wrong — a delta applied to a row that was never populated leaves
     * a figure no further movement will heal. This is the repair: it derives the split the same
     * way the reconciliation report derives the scalars, so both halves end up telling one story.
     *
     * <p>Warehouses the item no longer has instances in are zeroed rather than deleted, since a
     * row at zero is the honest statement that a shelf is empty. In-transit is left untouched: a
     * transfer moves counters without moving instances, so nothing derivable from instances can
     * speak for goods on a vehicle.
     */
    private int rebuildWarehouseRows(InventoryItem item) {
        int itemId = item.getInventoryItemId();
        int corrected = 0;
        Map<Long, BigDecimal[]> derived = new HashMap<>();
        for (Object[] row : inventoryInstanceRepository.sumByWarehouse(itemId)) {
            Long warehouseId = ((Number) row[0]).longValue();
            derived.put(warehouseId, new BigDecimal[]{
                    new BigDecimal(row[1].toString()),
                    new BigDecimal(row[2].toString())});
        }

        for (ItemWarehouseStock existing : itemWarehouseStockRepository.findByItem(itemId)) {
            BigDecimal[] figures = derived.remove(existing.getWarehouse().getId());
            BigDecimal onHand   = figures != null ? figures[0] : BigDecimal.ZERO;
            BigDecimal reserved = figures != null ? figures[1] : BigDecimal.ZERO;
            if (existing.getOnHand().compareTo(onHand) == 0
                    && existing.getReserved().compareTo(reserved) == 0) {
                continue;
            }
            logger.warn("WAREHOUSE CORRECTION {} at {} | onHand {} -> {} | reserved {} -> {}",
                    item.getItemCode(), existing.getWarehouse().getCode(),
                    existing.getOnHand(), onHand, existing.getReserved(), reserved);
            existing.setOnHand(onHand);
            existing.setReserved(reserved);
            existing.setUpdatedDate(new Date());
            itemWarehouseStockRepository.save(existing);
            corrected++;
        }

        // Instances in a warehouse that has no row yet: the receipt that put them there predates
        // the counters, so create the row rather than leaving the stock invisible.
        for (Map.Entry<Long, BigDecimal[]> missing : derived.entrySet()) {
            Warehouse warehouse = warehouseRepository.findById(missing.getKey()).orElse(null);
            if (warehouse == null) continue;
            ItemWarehouseStock fresh = new ItemWarehouseStock();
            fresh.setInventoryItem(item);
            fresh.setWarehouse(warehouse);
            fresh.setOnHand(missing.getValue()[0]);
            fresh.setReserved(missing.getValue()[1]);
            fresh.setUpdatedDate(new Date());
            itemWarehouseStockRepository.save(fresh);
            logger.warn("WAREHOUSE CORRECTION {} at {} | row created at onHand {} reserved {}",
                    item.getItemCode(), warehouse.getCode(), fresh.getOnHand(), fresh.getReserved());
            corrected++;
        }
        return corrected;
    }

    private void writeLedger(InventoryTransactionDTO req, InventoryItem item, double movement, double closingBalance) {
        double rate = effectiveRate(req, item);
        InventoryLedger ledger = new InventoryLedger();
        ledger.setMovementDate(req.getMovementDate() != null ? req.getMovementDate() : LocalDate.now());
        ledger.setTransactionType(req.getTransactionType());
        ledger.setQuantity(movement);
        ledger.setRate(rate);
        ledger.setAmount(Math.abs(movement) * rate);
        ledger.setValuationMethod("AVERAGE");
        // Blank resolves to the default warehouse; an unknown code throws rather than
        // silently parking the movement in the main store.
        ledger.setWarehouse(warehouseService.resolveByCodeOrDefault(req.getWarehouse()));
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

    // ─── RECONCILIATION ───────────────────────────────────────────────────────

    /**
     * Reports, per item, how far the stock counters have drifted from the instance rows
     * behind them. Read-only by design: it surfaces the divergence so it can be judged
     * before anything overwrites it.
     */
    @Override
    public StockReconciliationReportDto reconcileStockScalars(boolean onlyDrifted) {
        List<StockReconciliationRowDto> rows = new ArrayList<>();
        long itemsTotal = 0, itemsExamined = 0, itemsSkipped = 0, itemsScalarOnly = 0,
             itemsDrifted = 0, itemsSplitDrifted = 0;

        for (InventoryItem item : inventoryItemRepository.findAllByDeletedDateIsNull()) {
            itemsTotal++;
            ProductInventorySettings settings = item.getProductInventorySettings();
            if (settings == null) {
                // The relation is optional. Counted rather than swallowed, so an empty
                // report can be told apart from a report that examined nothing.
                itemsSkipped++;
                continue;
            }
            itemsExamined++;

            int itemId = item.getInventoryItemId();
            long liveInstances = inventoryInstanceRepository.countLiveInstances(itemId);

            double derivedAvailable = inventoryInstanceRepository.sumAvailableQuantity(itemId);
            double derivedReserved  = inventoryInstanceRepository.sumReservedQuantity(itemId);
            double scalarAvailable  = settings.getAvailableQuantity();
            double scalarReserved   = settings.getReservedQuantity();

            // Second, independent check: the company-wide counters must equal the sum of this
            // item's per-warehouse rows. This one holds even for items with no instances.
            double warehouseOnHand    = itemWarehouseStockRepository.sumOnHandForItem(itemId).doubleValue();
            double warehouseInTransit = itemWarehouseStockRepository.sumInTransitForItem(itemId).doubleValue();
            double warehouseReserved  = itemWarehouseStockRepository.sumReservedForItem(itemId).doubleValue();

            StockReconciliationRowDto row = new StockReconciliationRowDto(
                    itemId,
                    item.getItemCode(),
                    item.getName(),
                    scalarAvailable,
                    derivedAvailable,
                    scalarAvailable - derivedAvailable,
                    scalarReserved,
                    derivedReserved,
                    scalarReserved - derivedReserved,
                    liveInstances,
                    liveInstances == 0,
                    warehouseOnHand,
                    warehouseInTransit,
                    warehouseReserved,
                    // In-transit is still owned, so it belongs on the on-hand side of the sum.
                    scalarAvailable - (warehouseOnHand + warehouseInTransit),
                    scalarReserved - warehouseReserved);

            if (liveInstances == 0) itemsScalarOnly++;
            if (row.hasDrift()) itemsDrifted++;
            if (row.hasWarehouseSplitDrift()) itemsSplitDrifted++;

            if (!onlyDrifted || row.hasDrift() || row.hasWarehouseSplitDrift()) {
                rows.add(row);
            }
        }

        logger.info("Stock reconciliation: {} of {} items examined ({} skipped, no settings), "
                        + "{} drifted, {} split-drifted, {} counter-only; {} rows returned (onlyDrifted={})",
                itemsExamined, itemsTotal, itemsSkipped, itemsDrifted, itemsSplitDrifted,
                itemsScalarOnly, rows.size(), onlyDrifted);

        if (itemsExamined == 0) {
            logger.warn("Stock reconciliation examined 0 items — a clean result here proves nothing. "
                    + "{} active items exist, all without inventory settings.", itemsTotal);
        }

        return new StockReconciliationReportDto(
                itemsTotal, itemsExamined, itemsSkipped, itemsScalarOnly, itemsDrifted,
                itemsSplitDrifted, rows);
    }

    @Override
    @Transactional
    public List<StockScalarCorrectionRowDto> correctStockScalars(List<Integer> itemIds, String reason) {
        List<StockScalarCorrectionRowDto> results = new ArrayList<>();
        if (itemIds == null || itemIds.isEmpty()) return results;

        for (Integer itemId : itemIds) {
            InventoryItem item = inventoryItemRepository.findByActiveId(itemId);
            if (item == null) {
                results.add(new StockScalarCorrectionRowDto(itemId, null, 0, 0, 0, 0,
                        false, "No active item with this id"));
                continue;
            }
            ProductInventorySettings settings = item.getProductInventorySettings();
            if (settings == null) {
                results.add(new StockScalarCorrectionRowDto(itemId, item.getItemCode(), 0, 0, 0, 0,
                        false, "Item has no inventory settings to correct"));
                continue;
            }

            double prevAvailable = settings.getAvailableQuantity();
            double prevReserved  = settings.getReservedQuantity();
            double newAvailable  = inventoryInstanceRepository.sumAvailableQuantity(itemId);
            double newReserved   = inventoryInstanceRepository.sumReservedQuantity(itemId);

            boolean scalarsAgree = Math.abs(prevAvailable - newAvailable) < STOCK_EPSILON
                    && Math.abs(prevReserved - newReserved) < STOCK_EPSILON;

            if (!scalarsAgree) {
                settings.setAvailableQuantity(newAvailable);
                settings.setReservedQuantity(newReserved);
                inventoryItemRepository.save(item);
                logger.warn("SCALAR CORRECTION {} | available {} -> {} | reserved {} -> {} | reason: {}",
                        item.getItemCode(), prevAvailable, newAvailable, prevReserved, newReserved,
                        reason == null ? "(none given)" : reason);
            }

            // The warehouse rows are rebuilt whether or not the scalars moved: the two halves
            // drift independently, and the case that sent us looking for this tool was a warehouse
            // row left wrong while the item-wide counters were already right.
            int warehouseRowsFixed = rebuildWarehouseRows(item);

            String message;
            if (!scalarsAgree && warehouseRowsFixed > 0) {
                message = "Corrected, and " + warehouseRowsFixed + " warehouse row(s) rebuilt";
            } else if (!scalarsAgree) {
                message = "Corrected";
            } else if (warehouseRowsFixed > 0) {
                message = "Counters already agreed; " + warehouseRowsFixed + " warehouse row(s) rebuilt";
            } else {
                message = "Already agrees with instance state; left untouched";
            }

            results.add(new StockScalarCorrectionRowDto(itemId, item.getItemCode(),
                    prevAvailable, newAvailable, prevReserved, newReserved,
                    !scalarsAgree || warehouseRowsFixed > 0, message));
        }
        return results;
    }
}
