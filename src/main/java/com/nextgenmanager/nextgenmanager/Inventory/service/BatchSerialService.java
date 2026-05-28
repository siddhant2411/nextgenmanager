package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.BatchNumberDTO;
import com.nextgenmanager.nextgenmanager.Inventory.dto.SerialNumberDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.BatchNumber;
import com.nextgenmanager.nextgenmanager.Inventory.model.SerialNumber;
import com.nextgenmanager.nextgenmanager.Inventory.model.SerialStatus;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface BatchSerialService {

    /**
     * Create a batch record and auto-generate (or reuse) a batch number.
     *
     * @param item            the item being stocked
     * @param qty             total quantity in this batch
     * @param source          "GRN" | "WORK_ORDER" | "MANUAL"
     * @param sourceDocNo     GRN number or Work Order number
     * @param warehouse       receiving warehouse
     * @param manufacturingDate manufacturing / production date (nullable)
     * @param expiryDate      expiry date from GRN or item settings (nullable)
     * @param supplierBatchNo supplier's own batch number (nullable, GRN only)
     * @param createdBy       user
     */
    BatchNumber createBatch(InventoryItem item, double qty, String source, String sourceDocNo,
                             String warehouse, LocalDate manufacturingDate, LocalDate expiryDate,
                             String supplierBatchNo, String createdBy);

    /**
     * Create serial number records for each unit.
     *
     * @param item            the item
     * @param count           number of serials to create (= acceptedQty)
     * @param batch           parent batch (nullable if not batch-tracked)
     * @param source          "GRN" | "WORK_ORDER" | "MANUAL"
     * @param sourceDocNo     document reference
     * @param warehouse       location
     * @param manualSerials   optional list of serial numbers provided by user/supplier;
     *                        if provided, size must equal count; if null → auto-generate all
     * @param createdBy       user
     */
    List<SerialNumber> createSerials(InventoryItem item, int count, BatchNumber batch,
                                     String source, String sourceDocNo, String warehouse,
                                     List<String> manualSerials, String createdBy);

    /** Mark a serial as consumed, linking it to the consuming document (WO/SO). */
    void consumeSerial(String serialNumber, String consumedByDocNo);

    /** Paginated batch list for an item, optionally filtered by status. */
    Page<BatchNumberDTO> getBatchesForItem(int itemId, String status, Pageable pageable);

    /** Paginated serial list for an item, optionally filtered by status and search string. */
    Page<SerialNumberDTO> getSerialsForItem(int itemId, String status, String search, Pageable pageable);

    /** All serials belonging to a batch. */
    List<SerialNumberDTO> getSerialsForBatch(Long batchId);

    /** All batches created for a specific source document (GRN or WO number). */
    List<BatchNumberDTO> getBatchesForDocument(String sourceDocNo);

    /** All serials created for a specific source document (GRN or WO number). */
    List<SerialNumberDTO> getSerialsForDocument(String sourceDocNo);

    /**
     * Look up a single serial number by its serial string.
     * Returns the full lifecycle DTO including sourceDocNo (WO/GRN) and consumedByDocNo (DN/SO).
     */
    SerialNumberDTO getSerialByNumber(String serialNumber);

    /**
     * All serials that were dispatched / consumed via a specific document
     * (e.g. a Delivery Note number). Useful for showing what serials went out on a DN.
     */
    List<SerialNumberDTO> getSerialsConsumedByDocument(String consumedByDocNo);
}
