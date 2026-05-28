package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.InventoryTransactionDTO;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockCountLine;
import com.nextgenmanager.nextgenmanager.Inventory.dto.StockCountRequest;
import com.nextgenmanager.nextgenmanager.Inventory.service.InventoryTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresInventoryAdminAccess;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Physical Stock Count — POST /api/inventory/stock-count
 *
 * For each line the controller:
 *   1. Fetches the current system closing balance from the ledger.
 *   2. Calculates delta = physicalQty − systemQty.
 *   3. Calls adjustStock() with type=ADJUSTMENT / referenceType=STOCK_COUNT.
 *
 * No new migration is needed; the ADJUSTMENT transaction type already exists
 * in InventoryLedger.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiresInventoryAdminAccess
public class StockCountController {

    private static final Logger logger = LoggerFactory.getLogger(StockCountController.class);

    @Autowired
    private InventoryTransactionService inventoryTransactionService;

    @PostMapping("/stock-count")
    public ResponseEntity<Map<String, Object>> submitStockCount(@RequestBody StockCountRequest request) {

        // Generate a count reference ID: SC-YYYYMMDD-<random 4-digit>
        String countId = "SC-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + String.format("%04d", (int) (Math.random() * 9000) + 1000);

        String countedBy = request.getCountedBy() != null ? request.getCountedBy() : "system";

        int total = 0, adjusted = 0, noChange = 0;
        List<Map<String, Object>> lineResults = new ArrayList<>();

        for (StockCountLine line : request.getLines()) {
            Map<String, Object> result = new HashMap<>();
            result.put("inventoryItemId", line.getInventoryItemId());

            try {
                double systemQty = inventoryTransactionService.getCurrentStock(line.getInventoryItemId(), request.getWarehouse());
                double delta = line.getPhysicalQty() - systemQty;

                result.put("systemQty",   systemQty);
                result.put("physicalQty", line.getPhysicalQty());
                result.put("delta",       delta);

                if (Math.abs(delta) < 0.0001) {
                    result.put("status", "NO_CHANGE");
                    noChange++;
                } else {
                    InventoryTransactionDTO dto = new InventoryTransactionDTO();
                    dto.setInventoryItemId(line.getInventoryItemId());
                    dto.setQuantity(delta);
                    dto.setTransactionType("ADJUSTMENT");
                    dto.setReferenceType("STOCK_COUNT");
                    dto.setReferenceDocNo(countId);
                    dto.setCreatedBy(countedBy);
                    dto.setWarehouse(request.getWarehouse());
                    dto.setOverrideReason(line.getRemarks() != null ? line.getRemarks()
                            : (request.getRemarks() != null ? request.getRemarks() : "Physical stock count"));

                    inventoryTransactionService.adjustStock(dto);
                    result.put("status", "ADJUSTED");
                    adjusted++;
                }
            } catch (Exception e) {
                logger.error("Stock count failed for itemId={}: {}", line.getInventoryItemId(), e.getMessage());
                result.put("status", "ERROR");
                result.put("error",  e.getMessage());
            }

            lineResults.add(result);
            total++;
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("countId",    countId);
        summary.put("countedBy",  countedBy);
        summary.put("countDate",  LocalDate.now().toString());
        summary.put("totalLines", total);
        summary.put("adjusted",   adjusted);
        summary.put("noChange",   noChange);
        summary.put("errors",     total - adjusted - noChange);
        summary.put("lines",      lineResults);

        logger.info("Stock count {} completed: {} lines, {} adjusted, {} no-change", countId, total, adjusted, noChange);
        return ResponseEntity.ok(summary);
    }
}
