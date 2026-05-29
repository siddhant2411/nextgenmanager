package com.nextgenmanager.nextgenmanager.Inventory.controller;

import com.nextgenmanager.nextgenmanager.Inventory.dto.BatchNumberDTO;
import com.nextgenmanager.nextgenmanager.Inventory.dto.SerialNumberDTO;
import com.nextgenmanager.nextgenmanager.Inventory.service.BatchSerialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresOperationsAccess;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/batch-serial")
@RequiresOperationsAccess
public class BatchSerialController {

    @Autowired private BatchSerialService batchSerialService;

    // ── Batch endpoints ───────────────────────────────────────────────────────

    /**
     * GET /api/batch-serial/batches?itemId=1&status=ACTIVE&page=0&size=20
     * Returns all batches for an item, optionally filtered by status.
     */
    @GetMapping("/batches")
    public ResponseEntity<Page<BatchNumberDTO>> getBatches(
            @RequestParam int itemId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<BatchNumberDTO> result = batchSerialService.getBatchesForItem(
                itemId, status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate")));
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/batch-serial/batches/by-document?docNo=GRN-202505-0001
     * Returns all batches created from a specific GRN or Work Order.
     */
    @GetMapping("/batches/by-document")
    public ResponseEntity<List<BatchNumberDTO>> getBatchesByDocument(@RequestParam String docNo) {
        return ResponseEntity.ok(batchSerialService.getBatchesForDocument(docNo));
    }

    // ── Serial endpoints ──────────────────────────────────────────────────────

    /**
     * GET /api/batch-serial/serials?itemId=1&status=AVAILABLE&search=SN-RM&page=0&size=20
     * Returns serials for an item, optionally filtered by status and search string.
     */
    @GetMapping("/serials")
    public ResponseEntity<Page<SerialNumberDTO>> getSerials(
            @RequestParam int itemId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<SerialNumberDTO> result = batchSerialService.getSerialsForItem(
                itemId, status, search, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate")));
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/batch-serial/serials/by-batch?batchId=5
     * Returns all serial numbers that belong to a batch.
     */
    @GetMapping("/serials/by-batch")
    public ResponseEntity<List<SerialNumberDTO>> getSerialsByBatch(@RequestParam Long batchId) {
        return ResponseEntity.ok(batchSerialService.getSerialsForBatch(batchId));
    }

    /**
     * GET /api/batch-serial/serials/by-document?docNo=WO-42
     * Returns all serials created from a specific GRN or Work Order (sourceDocNo lookup).
     */
    @GetMapping("/serials/by-document")
    public ResponseEntity<List<SerialNumberDTO>> getSerialsByDocument(@RequestParam String docNo) {
        return ResponseEntity.ok(batchSerialService.getSerialsForDocument(docNo));
    }

    /**
     * GET /api/batch-serial/serials/consumed-by?docNo=DC/2025/0001
     * Returns all serials dispatched on a specific Delivery Note (or consumed by any document).
     * Use this to see exactly which serial numbers went out on a dispatch.
     */
    @GetMapping("/serials/consumed-by")
    public ResponseEntity<List<SerialNumberDTO>> getSerialsConsumedByDocument(@RequestParam String docNo) {
        return ResponseEntity.ok(batchSerialService.getSerialsConsumedByDocument(docNo));
    }

    /**
     * GET /api/batch-serial/serials/lookup?sn=SN-FG001-202505-000001
     * Full lifecycle of a single serial: where it was created (sourceDocNo / source)
     * and where it went (consumedByDocNo / consumedDate).
     */
    @GetMapping("/serials/lookup")
    public ResponseEntity<SerialNumberDTO> getSerialLifecycle(@RequestParam String sn) {
        return ResponseEntity.ok(batchSerialService.getSerialByNumber(sn));
    }
}
