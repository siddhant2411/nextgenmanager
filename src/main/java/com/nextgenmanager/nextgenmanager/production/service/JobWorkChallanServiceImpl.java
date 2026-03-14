package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import com.nextgenmanager.nextgenmanager.contact.repository.ContactRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.production.dto.*;
import com.nextgenmanager.nextgenmanager.production.enums.ChallanStatus;
import com.nextgenmanager.nextgenmanager.production.model.*;
import com.nextgenmanager.nextgenmanager.production.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class JobWorkChallanServiceImpl implements JobWorkChallanService {

    @Autowired private JobWorkChallanRepository challanRepo;
    @Autowired private JobWorkChallanLineRepository lineRepo;
    @Autowired private ContactRepository contactRepo;
    @Autowired private InventoryItemRepository itemRepo;
    @Autowired private WorkOrderRepository workOrderRepo;
    @Autowired private WorkOrderOperationRepository operationRepo;

    // ─── Create ──────────────────────────────────────────────────────────────

    @Override
    public JobWorkChallanDTO create(JobWorkChallanRequestDTO req) {
        JobWorkChallan challan = new JobWorkChallan();
        challan.setChallanNumber(generateChallanNumber());
        populateChallan(challan, req);
        challan.setStatus(ChallanStatus.DRAFT);
        return toDTO(challanRepo.save(challan));
    }

    // ─── Update (DRAFT only) ──────────────────────────────────────────────────

    @Override
    public JobWorkChallanDTO update(Long id, JobWorkChallanRequestDTO req) {
        JobWorkChallan challan = findActive(id);
        if (challan.getStatus() != ChallanStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT challans can be edited. Current status: " + challan.getStatus());
        }
        challan.getLines().clear();
        populateChallan(challan, req);
        return toDTO(challanRepo.save(challan));
    }

    // ─── Dispatch ─────────────────────────────────────────────────────────────

    @Override
    public JobWorkChallanDTO dispatch(Long id) {
        JobWorkChallan challan = findActive(id);
        if (challan.getStatus() != ChallanStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT challans can be dispatched. Current status: " + challan.getStatus());
        }
        if (challan.getLines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot dispatch a challan with no material lines.");
        }

        Date today = new Date();
        challan.setDispatchDate(today);

        // GST Rule 45 — material must return within 180 days
        LocalDate returnBy = LocalDate.now().plusDays(180);
        challan.setExpectedReturnDate(Date.from(returnBy.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        challan.setStatus(ChallanStatus.DISPATCHED);

        return toDTO(challanRepo.save(challan));
    }

    // ─── Receive Back ─────────────────────────────────────────────────────────

    @Override
    public JobWorkChallanDTO receiveBack(Long id, JobWorkChallanReceiptDTO receipt) {
        JobWorkChallan challan = findActive(id);
        if (challan.getStatus() != ChallanStatus.DISPATCHED
                && challan.getStatus() != ChallanStatus.PARTIALLY_RECEIVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Challan must be DISPATCHED or PARTIALLY_RECEIVED to record a receipt. Status: "
                            + challan.getStatus());
        }

        Date receiptDate = receipt.getReceiptDate() != null ? receipt.getReceiptDate() : new Date();

        for (JobWorkChallanLineReceiptDTO lineReceipt : receipt.getLines()) {
            JobWorkChallanLine line = challan.getLines().stream()
                    .filter(l -> l.getId().equals(lineReceipt.getLineId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Line id " + lineReceipt.getLineId() + " not found on this challan"));

            BigDecimal alreadyAccountedFor = line.getQuantityReceived().add(line.getQuantityRejected());
            BigDecimal pending = line.getQuantityDispatched().subtract(alreadyAccountedFor);
            BigDecimal totalThisReceipt = lineReceipt.getQuantityReceived()
                    .add(lineReceipt.getQuantityRejected() != null ? lineReceipt.getQuantityRejected() : BigDecimal.ZERO);

            if (totalThisReceipt.compareTo(pending) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Line " + lineReceipt.getLineId() + ": receipt qty (" + totalThisReceipt
                                + ") exceeds pending qty (" + pending + ")");
            }

            line.setQuantityReceived(line.getQuantityReceived().add(lineReceipt.getQuantityReceived()));
            if (lineReceipt.getQuantityRejected() != null) {
                line.setQuantityRejected(line.getQuantityRejected().add(lineReceipt.getQuantityRejected()));
            }
            line.setLastReceiptDate(receiptDate);
            if (lineReceipt.getRemarks() != null && !lineReceipt.getRemarks().isBlank()) {
                line.setRemarks(lineReceipt.getRemarks());
            }
        }

        // Determine if fully closed
        boolean allClosed = challan.getLines().stream().allMatch(l -> {
            BigDecimal accounted = l.getQuantityReceived().add(l.getQuantityRejected());
            return accounted.compareTo(l.getQuantityDispatched()) >= 0;
        });

        if (allClosed) {
            challan.setStatus(ChallanStatus.COMPLETED);
            challan.setActualReturnDate(receiptDate);
        } else {
            challan.setStatus(ChallanStatus.PARTIALLY_RECEIVED);
        }

        if (receipt.getRemarks() != null && !receipt.getRemarks().isBlank()) {
            challan.setRemarks(receipt.getRemarks());
        }

        return toDTO(challanRepo.save(challan));
    }

    // ─── Cancel ───────────────────────────────────────────────────────────────

    @Override
    public JobWorkChallanDTO cancel(Long id) {
        JobWorkChallan challan = findActive(id);
        if (challan.getStatus() == ChallanStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Completed challans cannot be cancelled.");
        }
        challan.setStatus(ChallanStatus.CANCELLED);
        return toDTO(challanRepo.save(challan));
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public JobWorkChallanDTO getById(Long id) {
        return toDTO(findActive(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobWorkChallanDTO> getAll() {
        return challanRepo.findByDeletedDateIsNullOrderByCreationDateDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobWorkChallanDTO> getByVendor(int vendorId) {
        return challanRepo.findByVendor_IdAndDeletedDateIsNullOrderByCreationDateDesc(vendorId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobWorkChallanDTO> getByWorkOrder(Long workOrderId) {
        return challanRepo.findByWorkOrder_IdAndDeletedDateIsNull(workOrderId.intValue())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobWorkChallanDTO> getByStatus(ChallanStatus status) {
        return challanRepo.findByStatusAndDeletedDateIsNull(status)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobWorkChallanDTO> getOverdue() {
        return challanRepo.findOverdue(new Date())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        JobWorkChallan challan = findActive(id);
        if (challan.getStatus() != ChallanStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only DRAFT challans can be deleted.");
        }
        challan.setDeletedDate(new Date());
        challanRepo.save(challan);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private JobWorkChallan findActive(Long id) {
        JobWorkChallan c = challanRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "JobWorkChallan not found: " + id));
        if (c.getDeletedDate() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "JobWorkChallan not found: " + id);
        }
        return c;
    }

    private void populateChallan(JobWorkChallan challan, JobWorkChallanRequestDTO req) {
        Contact vendor = contactRepo.findById(req.getVendorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vendor contact not found: " + req.getVendorId()));
        challan.setVendor(vendor);

        if (req.getWorkOrderId() != null) {
            WorkOrder wo = workOrderRepo.findById(req.getWorkOrderId().intValue())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "WorkOrder not found: " + req.getWorkOrderId()));
            challan.setWorkOrder(wo);
        } else {
            challan.setWorkOrder(null);
        }

        if (req.getWorkOrderOperationId() != null) {
            WorkOrderOperation op = operationRepo.findById(req.getWorkOrderOperationId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "WorkOrderOperation not found: " + req.getWorkOrderOperationId()));
            challan.setWorkOrderOperation(op);
        } else {
            challan.setWorkOrderOperation(null);
        }

        challan.setAgreedRatePerUnit(req.getAgreedRatePerUnit());
        challan.setDispatchDetails(req.getDispatchDetails());
        challan.setRemarks(req.getRemarks());

        List<JobWorkChallanLine> lines = new ArrayList<>();
        for (JobWorkChallanLineRequestDTO lineReq : req.getLines()) {
            JobWorkChallanLine line = new JobWorkChallanLine();
            line.setChallan(challan);

            if (lineReq.getInventoryItemId() != null) {
                InventoryItem item = itemRepo.findById(lineReq.getInventoryItemId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "InventoryItem not found: " + lineReq.getInventoryItemId()));
                line.setItem(item);
                // Auto-populate from item master if not provided
                if (lineReq.getHsnCode() == null || lineReq.getHsnCode().isBlank()) {
                    line.setHsnCode(item.getHsnCode());
                } else {
                    line.setHsnCode(lineReq.getHsnCode());
                }
                if (lineReq.getUom() == null || lineReq.getUom().isBlank()) {
                    line.setUom(item.getUom() != null ? item.getUom().name() : null);
                } else {
                    line.setUom(lineReq.getUom());
                }
                if (lineReq.getDescription() == null || lineReq.getDescription().isBlank()) {
                    line.setDescription(item.getName());
                } else {
                    line.setDescription(lineReq.getDescription());
                }
            } else {
                if (lineReq.getDescription() == null || lineReq.getDescription().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Each line must have either an inventoryItemId or a description.");
                }
                line.setHsnCode(lineReq.getHsnCode());
                line.setUom(lineReq.getUom());
                line.setDescription(lineReq.getDescription());
            }

            line.setQuantityDispatched(lineReq.getQuantityDispatched());
            line.setQuantityReceived(BigDecimal.ZERO);
            line.setQuantityRejected(BigDecimal.ZERO);
            line.setValuePerUnit(lineReq.getValuePerUnit());
            line.setRemarks(lineReq.getRemarks());
            lines.add(line);
        }
        challan.getLines().addAll(lines);
    }

    /** Generate challan number: JWC/2025-26/0001 (Indian financial year). */
    private String generateChallanNumber() {
        LocalDate today = LocalDate.now();
        int startYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        String fy = startYear + "-" + String.valueOf(startYear + 1).substring(2);
        String prefix = "JWC/" + fy + "/";

        List<String> existing = challanRepo.findLastChallanNumberByPrefix(prefix);
        int next = 1;
        if (!existing.isEmpty()) {
            String last = existing.get(0); // e.g., JWC/2025-26/0042
            String[] parts = last.split("/");
            next = Integer.parseInt(parts[parts.length - 1]) + 1;
        }
        return prefix + String.format("%04d", next);
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private JobWorkChallanDTO toDTO(JobWorkChallan c) {
        Long daysRemaining = null;
        if (c.getExpectedReturnDate() != null
                && (c.getStatus() == ChallanStatus.DISPATCHED
                || c.getStatus() == ChallanStatus.PARTIALLY_RECEIVED)) {
            LocalDate returnDate = c.getExpectedReturnDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), returnDate);
        }

        List<JobWorkChallanDTO.LineDTO> lineDTOs = c.getLines().stream().map(l -> {
            BigDecimal pending = l.getQuantityDispatched()
                    .subtract(l.getQuantityReceived())
                    .subtract(l.getQuantityRejected());

            return JobWorkChallanDTO.LineDTO.builder()
                    .id(l.getId())
                    .inventoryItemId(l.getItem() != null ? l.getItem().getInventoryItemId() : null)
                    .itemCode(l.getItem() != null ? l.getItem().getItemCode() : null)
                    .itemName(l.getItem() != null ? l.getItem().getName() : null)
                    .description(l.getDescription())
                    .hsnCode(l.getHsnCode())
                    .quantityDispatched(l.getQuantityDispatched())
                    .quantityReceived(l.getQuantityReceived())
                    .quantityRejected(l.getQuantityRejected())
                    .quantityPending(pending)
                    .uom(l.getUom())
                    .valuePerUnit(l.getValuePerUnit())
                    .remarks(l.getRemarks())
                    .lastReceiptDate(l.getLastReceiptDate())
                    .build();
        }).collect(Collectors.toList());

        return JobWorkChallanDTO.builder()
                .id(c.getId())
                .challanNumber(c.getChallanNumber())
                .vendorId(c.getVendor().getId())
                .vendorName(c.getVendor().getCompanyName())
                .vendorGstNumber(c.getVendor().getGstNumber())
                .workOrderId(c.getWorkOrder() != null ? (long) c.getWorkOrder().getId() : null)
                .workOrderNumber(c.getWorkOrder() != null ? c.getWorkOrder().getWorkOrderNumber() : null)
                .workOrderOperationId(c.getWorkOrderOperation() != null ? c.getWorkOrderOperation().getId() : null)
                .workOrderOperationName(c.getWorkOrderOperation() != null ? c.getWorkOrderOperation().getOperationName() : null)
                .status(c.getStatus())
                .dispatchDate(c.getDispatchDate())
                .expectedReturnDate(c.getExpectedReturnDate())
                .actualReturnDate(c.getActualReturnDate())
                .daysRemainingForReturn(daysRemaining)
                .agreedRatePerUnit(c.getAgreedRatePerUnit())
                .dispatchDetails(c.getDispatchDetails())
                .remarks(c.getRemarks())
                .lines(lineDTOs)
                .creationDate(c.getCreationDate())
                .updatedDate(c.getUpdatedDate())
                .build();
    }
}
