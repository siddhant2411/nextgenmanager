package com.nextgenmanager.nextgenmanager.Inventory.service;

import com.nextgenmanager.nextgenmanager.Inventory.dto.BatchNumberDTO;
import com.nextgenmanager.nextgenmanager.Inventory.dto.SerialNumberDTO;
import com.nextgenmanager.nextgenmanager.Inventory.model.*;
import com.nextgenmanager.nextgenmanager.Inventory.repository.BatchNumberRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.NumberSequenceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.SerialNumberRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import java.math.BigDecimal;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class BatchSerialServiceImpl implements BatchSerialService {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired private BatchNumberRepository batchRepo;
    @Autowired private SerialNumberRepository serialRepo;
    @Autowired private NumberSequenceRepository sequenceRepo;

    // ─── Batch creation ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public BatchNumber createBatch(InventoryItem item, double qty, String source, String sourceDocNo,
                                    String warehouse, LocalDate manufacturingDate, LocalDate expiryDate,
                                    String supplierBatchNo, String createdBy) {
        String batchNo = generateBatchNumber(item.getItemCode());

        BatchNumber batch = new BatchNumber();
        batch.setBatchNumber(batchNo);
        batch.setInventoryItem(item);
        batch.setOriginalQty(BigDecimal.valueOf(qty));
        batch.setRemainingQty(BigDecimal.valueOf(qty));
        batch.setStatus(BatchStatus.ACTIVE);
        batch.setSource(source);
        batch.setSourceDocNo(sourceDocNo);
        batch.setWarehouse(warehouse);
        batch.setManufacturingDate(manufacturingDate);
        batch.setExpiryDate(expiryDate);
        batch.setSupplierBatchNo(supplierBatchNo);
        batch.setCreatedBy(createdBy);
        return batchRepo.save(batch);
    }

    // ─── Serial creation ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<SerialNumber> createSerials(InventoryItem item, int count, BatchNumber batch,
                                             String source, String sourceDocNo, String warehouse,
                                             List<String> manualSerials, String createdBy) {
        if (manualSerials != null && !manualSerials.isEmpty() && manualSerials.size() != count) {
            throw new IllegalArgumentException("manualSerials count (" + manualSerials.size()
                    + ") must equal acceptedQty (" + count + ")");
        }

        // Validate manual serials for duplicates upfront
        if (manualSerials != null) {
            for (String s : manualSerials) {
                if (serialRepo.existsBySerialNumber(s)) {
                    throw new IllegalArgumentException("Serial number already exists in system: " + s);
                }
            }
        }

        List<SerialNumber> created = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String sn = (manualSerials != null) ? manualSerials.get(i) : generateSerialNumber(item.getItemCode());

            SerialNumber serial = new SerialNumber();
            serial.setSerialNumber(sn);
            serial.setInventoryItem(item);
            serial.setBatch(batch);
            serial.setStatus(SerialStatus.AVAILABLE);
            serial.setReceivedDate(LocalDate.now());
            serial.setSource(source);
            serial.setSourceDocNo(sourceDocNo);
            serial.setWarehouse(warehouse);
            serial.setCreatedBy(createdBy);
            created.add(serialRepo.save(serial));
        }
        return created;
    }

    // ─── Consume serial ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void consumeSerial(String serialNumber, String consumedByDocNo) {
        SerialNumber sn = serialRepo.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Serial not found: " + serialNumber));
        sn.setStatus(SerialStatus.CONSUMED);
        sn.setConsumedDate(new Date());
        sn.setConsumedByDocNo(consumedByDocNo);
        serialRepo.save(sn);
    }

    // ─── Query methods ────────────────────────────────────────────────────────

    @Override
    public Page<BatchNumberDTO> getBatchesForItem(int itemId, String status, Pageable pageable) {
        BatchStatus bs = (status != null && !status.isBlank()) ? BatchStatus.valueOf(status.toUpperCase()) : null;
        return batchRepo.findByItemIdAndStatus(itemId, bs, pageable).map(this::toBatchDTO);
    }

    @Override
    public Page<SerialNumberDTO> getSerialsForItem(int itemId, String status, String search, Pageable pageable) {
        SerialStatus ss = (status != null && !status.isBlank()) ? SerialStatus.valueOf(status.toUpperCase()) : null;
        return serialRepo.searchByItem(itemId, ss, search, pageable).map(this::toSerialDTO);
    }

    @Override
    public List<SerialNumberDTO> getSerialsForBatch(Long batchId) {
        return serialRepo.findByBatch_Id(batchId).stream().map(this::toSerialDTO).toList();
    }

    @Override
    public List<BatchNumberDTO> getBatchesForDocument(String sourceDocNo) {
        return batchRepo.findBySourceDocNo(sourceDocNo).stream().map(this::toBatchDTO).toList();
    }

    @Override
    public List<SerialNumberDTO> getSerialsForDocument(String sourceDocNo) {
        return serialRepo.findBySourceDocNo(sourceDocNo).stream().map(this::toSerialDTO).toList();
    }

    @Override
    public SerialNumberDTO getSerialByNumber(String serialNumber) {
        SerialNumber sn = serialRepo.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Serial number not found: " + serialNumber));
        return toSerialDTO(sn);
    }

    @Override
    public List<SerialNumberDTO> getSerialsConsumedByDocument(String consumedByDocNo) {
        return serialRepo.findByConsumedByDocNo(consumedByDocNo).stream().map(this::toSerialDTO).toList();
    }

    // ─── Number generation ────────────────────────────────────────────────────

    @Transactional
    public String generateBatchNumber(String itemCode) {
        String yearMonth = LocalDate.now().format(YYYYMM);
        String key = "BATCH-" + itemCode + "-" + yearMonth;
        long seq = nextSequence(key);
        return String.format("BTH-%s-%s-%04d", itemCode, yearMonth, seq);
    }

    @Transactional
    public String generateSerialNumber(String itemCode) {
        String yearMonth = LocalDate.now().format(YYYYMM);
        String key = "SERIAL-" + itemCode + "-" + yearMonth;
        long seq = nextSequence(key);
        return String.format("SN-%s-%s-%06d", itemCode, yearMonth, seq);
    }

    /** Atomically get-and-increment the sequence for a given key. */
    private long nextSequence(String key) {
        NumberSequence seq = sequenceRepo.findByKeyForUpdate(key).orElseGet(() -> {
            NumberSequence ns = new NumberSequence(key, 1L);
            return sequenceRepo.save(ns);
        });
        long val = seq.getNextVal();
        seq.setNextVal(val + 1);
        sequenceRepo.save(seq);
        return val;
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private BatchNumberDTO toBatchDTO(BatchNumber b) {
        BatchNumberDTO dto = new BatchNumberDTO();
        dto.setId(b.getId());
        dto.setBatchNumber(b.getBatchNumber());
        dto.setInventoryItemId(b.getInventoryItem().getInventoryItemId());
        dto.setItemCode(b.getInventoryItem().getItemCode());
        dto.setItemName(b.getInventoryItem().getName());
        dto.setManufacturingDate(b.getManufacturingDate());
        dto.setExpiryDate(b.getExpiryDate());
        dto.setSupplierBatchNo(b.getSupplierBatchNo());
        dto.setOriginalQty(b.getOriginalQty() != null ? b.getOriginalQty().doubleValue() : 0.0);
        dto.setRemainingQty(b.getRemainingQty() != null ? b.getRemainingQty().doubleValue() : 0.0);
        dto.setStatus(b.getStatus().name());
        dto.setSource(b.getSource());
        dto.setSourceDocNo(b.getSourceDocNo());
        dto.setWarehouse(b.getWarehouse());
        dto.setCreatedBy(b.getCreatedBy());
        dto.setCreatedDate(b.getCreatedDate());
        return dto;
    }

    private SerialNumberDTO toSerialDTO(SerialNumber s) {
        SerialNumberDTO dto = new SerialNumberDTO();
        dto.setId(s.getId());
        dto.setSerialNumber(s.getSerialNumber());
        dto.setInventoryItemId(s.getInventoryItem().getInventoryItemId());
        dto.setItemCode(s.getInventoryItem().getItemCode());
        dto.setItemName(s.getInventoryItem().getName());
        if (s.getBatch() != null) {
            dto.setBatchId(s.getBatch().getId());
            dto.setBatchNumber(s.getBatch().getBatchNumber());
        }
        dto.setStatus(s.getStatus().name());
        dto.setReceivedDate(s.getReceivedDate());
        dto.setSource(s.getSource());
        dto.setSourceDocNo(s.getSourceDocNo());
        dto.setWarehouse(s.getWarehouse());
        dto.setConsumedDate(s.getConsumedDate());
        dto.setConsumedByDocNo(s.getConsumedByDocNo());
        dto.setCreatedBy(s.getCreatedBy());
        dto.setCreatedDate(s.getCreatedDate());
        return dto;
    }
}
