package com.nextgenmanager.nextgenmanager.quality.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.QualityStatus;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.quality.dto.NcrCreateRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.NcrDispositionRequest;
import com.nextgenmanager.nextgenmanager.quality.dto.NonConformanceReportDto;
import com.nextgenmanager.nextgenmanager.quality.model.*;
import com.nextgenmanager.nextgenmanager.quality.repository.InspectionLotRepository;
import com.nextgenmanager.nextgenmanager.quality.repository.NonConformanceReportRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NonConformanceServiceImpl implements NonConformanceService {

    private static final Logger logger = LoggerFactory.getLogger(NonConformanceServiceImpl.class);

    private final NonConformanceReportRepository ncrRepository;
    private final InspectionLotRepository inspectionLotRepository;
    private final InventoryInstanceRepository inventoryInstanceRepository;
    private final NcrNumberGenerator numberGenerator;

    @Override
    public List<NonConformanceReportDto> list(NcrStatus status) {
        List<NonConformanceReport> rows = status != null
                ? ncrRepository.findLiveByStatus(status)
                : ncrRepository.findAllLive();
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    public NonConformanceReportDto get(Long id) {
        return toDto(load(id));
    }

    @Override
    public List<NonConformanceReportDto> forLot(Long lotId) {
        return ncrRepository.findLiveByLot(lotId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public NonConformanceReportDto raise(NcrCreateRequest request) {
        if (request.problem() == null || request.problem().isBlank()) {
            throw new IllegalArgumentException(
                    "Say what is wrong: a report nobody can read gives nobody anything to act on");
        }
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        InspectionLot lot = inspectionLotRepository.findLiveById(request.inspectionLotId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Inspection lot not found: " + request.inspectionLotId()));

        // A report belongs to a failure. Raising one against a lot that passed would be a record
        // of a problem the inspection says does not exist.
        if (lot.getStatus() != InspectionLotStatus.FAILED) {
            throw new IllegalStateException(String.format(
                    "%s is %s — a non-conformance is raised against an inspection that failed",
                    lot.getLotNumber(), lot.getStatus()));
        }
        if (request.quantity().compareTo(lot.getQuantityOffered()) > 0) {
            throw new IllegalArgumentException(String.format(
                    "%s only offered %s for inspection", lot.getLotNumber(),
                    lot.getQuantityOffered().toPlainString()));
        }

        NonConformanceReport ncr = new NonConformanceReport();
        ncr.setInspectionLot(lot);
        ncr.setQuantity(request.quantity());
        ncr.setProblem(request.problem());
        ncr.setRemarks(request.remarks());
        ncr.setRaisedBy(request.raisedBy() != null ? request.raisedBy() : currentUser());
        ncr.setStatus(NcrStatus.OPEN);
        ncr.setNcrNumber(numberGenerator.next());

        NonConformanceReport saved = ncrRepository.save(ncr);
        logger.info("{} raised against {}: {}", saved.getNcrNumber(), lot.getLotNumber(),
                saved.getProblem());
        return toDto(saved);
    }

    @Override
    @Transactional
    public NonConformanceReportDto decide(Long id, NcrDispositionRequest request) {
        NonConformanceReport ncr = load(id);
        if (ncr.getStatus() == NcrStatus.CLOSED) {
            throw new IllegalStateException(String.format(
                    "%s is closed: it was %s. Raise a new report if something else is wrong.",
                    ncr.getNcrNumber(), ncr.getDisposition()));
        }
        if (request == null || request.disposition() == null) {
            throw new IllegalArgumentException("Say what is to be done with the goods");
        }
        if (request.disposition() == NcrDisposition.USE_AS_IS
                && (request.approvedBy() == null || request.approvedBy().isBlank())) {
            throw new IllegalArgumentException(
                    "Using goods as they are needs a name against it — this overrides the "
                            + "inspection rather than acting on it");
        }

        ncr.setDisposition(request.disposition());
        ncr.setDispositionedBy(request.dispositionedBy() != null
                ? request.dispositionedBy() : currentUser());
        ncr.setDispositionedDate(new Date());
        ncr.setDispositionNotes(request.notes());
        ncr.setApprovedBy(request.approvedBy());
        ncr.setStatus(NcrStatus.CLOSED);
        ncr.setUpdatedDate(new Date());

        applyToStock(ncr);

        NonConformanceReport saved = ncrRepository.save(ncr);
        logger.warn("{} dispositioned {} by {}{}", saved.getNcrNumber(), saved.getDisposition(),
                saved.getDispositionedBy(),
                saved.getApprovedBy() != null ? " (approved by " + saved.getApprovedBy() + ")" : "");
        return toDto(saved);
    }

    /**
     * Moves the quality status of the stock the report is about, where the disposition says
     * something unambiguous about it.
     *
     * <p>What it deliberately does not do is move quantities. Scrapping goods and sending them
     * back to a vendor are both stock movements with their own documents — a write-off adjustment
     * and a debit note — and inventing them here would put movements in the ledger that no
     * document explains. The stock stays FAILED, which keeps it off every pick, until one of
     * those is raised.
     */
    private void applyToStock(NonConformanceReport ncr) {
        InspectionLot lot = ncr.getInspectionLot();
        if (lot.getGoodsReceiptNote() == null) return;

        List<InventoryInstance> received = inventoryInstanceRepository
                .findFailedStock(lot.getInventoryItem().getInventoryItemId());
        if (received.isEmpty()) return;

        QualityStatus outcome = switch (ncr.getDisposition()) {
            case USE_AS_IS -> QualityStatus.WAIVED;   // released on somebody's authority
            case REWORK -> QualityStatus.PENDING_QC;  // to be looked at again once put right
            case SCRAP, RETURN_TO_VENDOR -> QualityStatus.FAILED; // stays unusable pending its own document
        };

        for (InventoryInstance inst : received) {
            inst.setQualityStatus(outcome);
            inventoryInstanceRepository.save(inst);
        }
        logger.info("{}: {} instance(s) of {} moved to {}", ncr.getNcrNumber(), received.size(),
                lot.getInventoryItem().getItemCode(), outcome);
    }

    private NonConformanceReport load(Long id) {
        return ncrRepository.findLiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id));
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {
            return "system";
        }
    }

    private NonConformanceReportDto toDto(NonConformanceReport n) {
        InspectionLot lot = n.getInspectionLot();
        return new NonConformanceReportDto(
                n.getId(), n.getNcrNumber(),
                lot != null ? lot.getId() : null,
                lot != null ? lot.getLotNumber() : null,
                lot != null && lot.getInventoryItem() != null ? lot.getInventoryItem().getItemCode() : null,
                lot != null && lot.getInventoryItem() != null ? lot.getInventoryItem().getName() : null,
                n.getQuantity(), n.getProblem(),
                n.getDisposition(), n.getDispositionedBy(), n.getDispositionedDate(),
                n.getDispositionNotes(), n.getApprovedBy(),
                n.getStatus(), n.getRaisedBy(), n.getRemarks(), n.getCreationDate());
    }
}
