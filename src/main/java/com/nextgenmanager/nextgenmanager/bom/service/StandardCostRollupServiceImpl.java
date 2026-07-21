package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class StandardCostRollupServiceImpl implements StandardCostRollupService {

    private static final Logger logger = LoggerFactory.getLogger(StandardCostRollupServiceImpl.class);

    @Autowired
    private BomRepository bomRepository;

    @Autowired
    private BomService bomService;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Override
    @Transactional
    public int rollUpAllStandardCosts() {
        Set<Integer> done = new HashSet<>();
        List<Integer> rootItemIds = bomRepository.findAllActiveBomParentItemIds();
        logger.info("Standard-cost roll-up starting for {} items with an active BOM", rootItemIds.size());
        for (Integer itemId : rootItemIds) {
            try {
                rollUp(itemId, done, new HashSet<>());
            } catch (Exception e) {
                // One bad BOM (e.g. a cycle) shouldn't abort the whole run.
                logger.error("Standard-cost roll-up failed for item {}: {}", itemId, e.getMessage());
            }
        }
        logger.info("Standard-cost roll-up finished; {} items recomputed", done.size());
        return done.size();
    }

    @Override
    @Transactional
    public BigDecimal rollUpItemStandardCost(int itemId) {
        return rollUp(itemId, new HashSet<>(), new HashSet<>());
    }

    /**
     * Post-order roll-up. Returns the fully-loaded unit cost of {@code itemId}; for manufactured items
     * (those with an active BOM) it also writes that cost to standardCost.
     *
     * @param done memoizes items already rolled in this run (shared sub-assemblies computed once)
     * @param path current recursion path, for circular-BOM detection
     */
    private BigDecimal rollUp(int itemId, Set<Integer> done, Set<Integer> path) {
        Optional<Bom> activeBom = bomRepository.findActiveBomWithPositionsByParentItemId(itemId);
        if (activeBom.isEmpty()) {
            // Leaf / purchased item — its stored standardCost is its cost; never overwrite it.
            return currentStandardCost(itemId);
        }
        if (path.contains(itemId)) {
            throw new IllegalStateException("Circular BOM detected at item " + itemId);
        }
        if (done.contains(itemId)) {
            return currentStandardCost(itemId);
        }

        path.add(itemId);
        Bom bom = activeBom.get();

        // Roll children first so this BOM's material lines read fresh child standard costs.
        if (bom.getPositions() != null) {
            for (BomPosition pos : bom.getPositions()) {
                if (pos.getChildInventoryItem() != null) {
                    rollUp(pos.getChildInventoryItem().getInventoryItemId(), done, path);
                }
            }
        }

        // Store PRIME cost (material incl. rolled child prime costs + conversion + additional, WITHOUT
        // overhead). Overhead is applied fresh at each level's own rate when a BOM is costed, so it must
        // not be baked into a stored cost — otherwise a parent would charge overhead on overhead.
        var breakdown = bomService.getBomCostBreakdown(bom.getId());
        BigDecimal overhead = breakdown.getOverheadCost() != null ? breakdown.getOverheadCost() : BigDecimal.ZERO;
        BigDecimal prime = breakdown.getTotalCost().subtract(overhead);
        writeStandardCost(itemId, prime);

        path.remove(itemId);
        done.add(itemId);
        return prime;
    }

    private BigDecimal currentStandardCost(int itemId) {
        InventoryItem item = inventoryItemRepository.findByInventoryItemIdAndDeletedDateIsNull(itemId);
        if (item == null || item.getProductFinanceSettings() == null
                || item.getProductFinanceSettings().getStandardCost() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(item.getProductFinanceSettings().getStandardCost());
    }

    private void writeStandardCost(int itemId, BigDecimal loaded) {
        InventoryItem item = inventoryItemRepository.findByInventoryItemIdAndDeletedDateIsNull(itemId);
        if (item == null) {
            logger.warn("Roll-up: item {} not found; skipping standardCost write", itemId);
            return;
        }
        ProductFinanceSettings fin = item.getProductFinanceSettings();
        if (fin == null) {
            fin = new ProductFinanceSettings();
            fin.setInventoryItem(item);
            item.setProductFinanceSettings(fin);
        }
        fin.setStandardCost(loaded.setScale(2, RoundingMode.HALF_UP).doubleValue());
        fin.setLastUpdatedOn(new Date());
        // Flush so the next level's breakdown reads this fresh value.
        inventoryItemRepository.saveAndFlush(item);
    }
}
