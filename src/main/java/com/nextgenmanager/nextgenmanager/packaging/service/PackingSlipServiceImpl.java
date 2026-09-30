package com.nextgenmanager.nextgenmanager.packaging.service;

import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstance;
import com.nextgenmanager.nextgenmanager.Inventory.model.InventoryInstanceStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickList;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListLine;
import com.nextgenmanager.nextgenmanager.Inventory.model.PickListStatus;
import com.nextgenmanager.nextgenmanager.Inventory.model.QualityStatus;
import com.nextgenmanager.nextgenmanager.Inventory.repository.InventoryInstanceRepository;
import com.nextgenmanager.nextgenmanager.Inventory.repository.PickListRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.packaging.dto.*;
import com.nextgenmanager.nextgenmanager.packaging.model.PackageBox;
import com.nextgenmanager.nextgenmanager.packaging.model.PackageLine;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlip;
import com.nextgenmanager.nextgenmanager.packaging.model.PackingSlipStatus;
import com.nextgenmanager.nextgenmanager.packaging.repository.PackageBoxRepository;
import com.nextgenmanager.nextgenmanager.packaging.repository.PackageLineRepository;
import com.nextgenmanager.nextgenmanager.packaging.repository.PackingSlipRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Packing slips and the boxes on them.
 *
 * <p>A slip packs exactly one confirmed pick — the same one-trip reasoning that gives a pick list
 * one warehouse. Packing allocates nothing of its own: a box line can only take instances already
 * sitting on a line of that pick, and only up to what that line's quantityPicked leaves unpacked.
 * Closing a slip is gated by {@link PackagingGateService} the same way {@code completeWorkOrder} is
 * gated by {@code QualityGateService} — a box with a failed or unjudged PACKAGE lot stops it.
 */
@Service
@RequiredArgsConstructor
public class PackingSlipServiceImpl implements PackingSlipService {

    private static final Logger logger = LoggerFactory.getLogger(PackingSlipServiceImpl.class);

    private final PackingSlipRepository packingSlipRepository;
    private final PackageBoxRepository packageBoxRepository;
    private final PackageLineRepository packageLineRepository;
    private final PickListRepository pickListRepository;
    private final InventoryInstanceRepository inventoryInstanceRepository;
    private final PackagingGateService packagingGateService;
    private final PackingSlipNumberGenerator numberGenerator;

    // ─── Reads ────────────────────────────────────────────────────────────────

    @Override
    public List<PackingSlipDto> list(PackingSlipStatus status, Long salesOrderId) {
        List<PackingSlip> rows;
        if (status != null)            rows = packingSlipRepository.findLiveByStatus(status);
        else if (salesOrderId != null) rows = packingSlipRepository.findLiveBySalesOrder(salesOrderId);
        else                           rows = packingSlipRepository.findAllLive();
        return rows.stream().map(this::toDto).toList();
    }

    @Override
    public PackingSlipDto get(Long id) {
        return toDto(load(id));
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PackingSlipDto createFromPickList(PackingSlipCreateRequest request) {
        if (request == null || request.pickListId() == null) {
            throw new IllegalArgumentException("A pick list must be named");
        }
        PickList pick = pickListRepository.findLiveById(request.pickListId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pick list not found: " + request.pickListId()));

        // Only a confirmed pick has specific units allocated to box up. A DRAFT or RELEASED pick
        // has nothing physical behind it yet, and a DISPATCHED one already shipped without this
        // step — packing it now would describe boxes for stock that is already on a lorry.
        if (pick.getStatus() != PickListStatus.PICKED) {
            throw new IllegalStateException(String.format(
                    "Cannot pack %s: it is %s, not PICKED", pick.getPickNumber(), pick.getStatus()));
        }
        packingSlipRepository.findLiveByPickList(pick.getId()).ifPresent(existing -> {
            throw new IllegalStateException(String.format(
                    "%s already packs %s", existing.getSlipNumber(), pick.getPickNumber()));
        });

        PackingSlip slip = new PackingSlip();
        slip.setSlipNumber(numberGenerator.next());
        slip.setSalesOrder(pick.getSalesOrder());
        slip.setPickList(pick);
        slip.setStatus(PackingSlipStatus.DRAFT);
        slip.setRemarks(request.remarks());
        slip.setCreatedBy(currentUser());

        PackingSlip saved = packingSlipRepository.save(slip);
        logger.info("Packing slip {} opened against {}", saved.getSlipNumber(), pick.getPickNumber());
        return toDto(saved);
    }

    @Override
    @Transactional
    public PackageBoxDto addBox(Long slipId, PackageBoxCreateRequest request) {
        PackingSlip slip = load(slipId);
        if (slip.getStatus() != PackingSlipStatus.DRAFT) {
            throw new IllegalStateException(String.format(
                    "Cannot add a box to %s: it is %s, not DRAFT", slip.getSlipNumber(), slip.getStatus()));
        }
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("A box needs at least one line");
        }

        PickList pick = slip.getPickList();

        PackageBox box = new PackageBox();
        box.setPackingSlip(slip);
        box.setBoxNumber(packageBoxRepository.maxBoxNumber(slip.getId()) + 1);
        box.setBoxType(request.boxType());
        box.setLengthCm(request.lengthCm());
        box.setWidthCm(request.widthCm());
        box.setHeightCm(request.heightCm());
        box.setGrossWeightKg(request.grossWeightKg());
        box.setNetWeightKg(request.netWeightKg());
        box.setShippingMarks(request.shippingMarks());

        for (PackageLineRequest lineReq : request.lines()) {
            box.getLines().add(toPackageLine(pick, box, lineReq));
        }

        PackageBox saved = packageBoxRepository.save(box);
        slip.getBoxes().add(saved);
        logger.info("{} box {} packed with {} line(s)", slip.getSlipNumber(), saved.getBoxNumber(),
                saved.getLines().size());
        return toDto(saved);
    }

    @Override
    @Transactional
    public PackingSlipDto pack(Long slipId) {
        PackingSlip slip = load(slipId);
        if (slip.getStatus() != PackingSlipStatus.DRAFT) {
            throw new IllegalStateException(String.format(
                    "Cannot mark %s packed: it is %s, not DRAFT", slip.getSlipNumber(), slip.getStatus()));
        }
        if (slip.getBoxes().stream().noneMatch(b -> b.getDeletedDate() == null)) {
            throw new IllegalStateException(slip.getSlipNumber() + " has no boxes yet");
        }
        slip.setStatus(PackingSlipStatus.PACKED);
        slip.setPackedDate(new Date());
        slip.setPackedBy(currentUser());
        slip.setUpdatedDate(new Date());
        logger.info("Packing slip {} packed: {} box(es)", slip.getSlipNumber(), slip.getBoxes().size());
        return toDto(packingSlipRepository.save(slip));
    }

    @Override
    @Transactional
    public PackingSlipDto close(Long slipId) {
        PackingSlip slip = load(slipId);
        if (slip.getStatus() != PackingSlipStatus.PACKED) {
            throw new IllegalStateException(String.format(
                    "Cannot close %s: it is %s, not PACKED", slip.getSlipNumber(), slip.getStatus()));
        }
        packagingGateService.assertClosingAllowed(slip);

        slip.setStatus(PackingSlipStatus.CLOSED);
        slip.setClosedDate(new Date());
        slip.setClosedBy(currentUser());
        slip.setUpdatedDate(new Date());
        logger.info("Packing slip {} closed", slip.getSlipNumber());
        return toDto(packingSlipRepository.save(slip));
    }

    @Override
    @Transactional
    public void cancel(Long slipId) {
        PackingSlip slip = load(slipId);
        if (slip.getStatus() == PackingSlipStatus.CANCELLED) {
            throw new IllegalStateException(slip.getSlipNumber() + " is already cancelled");
        }
        if (slip.getStatus() == PackingSlipStatus.CLOSED) {
            throw new IllegalStateException(
                    slip.getSlipNumber() + " is closed — it shipped, or is ready to. Nothing to cancel.");
        }

        for (PackageBox box : slip.getBoxes()) {
            for (PackageLine line : box.getLines()) {
                for (InventoryInstance inst : inventoryInstanceRepository.findByPackageLineId(line.getId())) {
                    inst.setPackageLine(null);
                    inventoryInstanceRepository.save(inst);
                }
            }
        }

        slip.setStatus(PackingSlipStatus.CANCELLED);
        slip.setUpdatedDate(new Date());
        packingSlipRepository.save(slip);
        logger.info("Packing slip {} cancelled and its box allocations released", slip.getSlipNumber());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private PackingSlip load(Long id) {
        return packingSlipRepository.findLiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Packing slip not found: " + id));
    }

    private PackageLine toPackageLine(PickList pick, PackageBox box, PackageLineRequest req) {
        if (req.quantity() == null || req.quantity().signum() <= 0) {
            throw new IllegalArgumentException("Packed quantity must be greater than zero");
        }

        PickListLine pickLine = null;
        InventoryItem item;
        if (req.pickListLineId() != null) {
            pickLine = pick.getLines().stream()
                    .filter(l -> l.getId().equals(req.pickListLineId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Pick line " + req.pickListLineId() + " is not on " + pick.getPickNumber()));
            item = pickLine.getInventoryItem();

            BigDecimal already = packageLineRepository.sumAlreadyPackaged(pickLine.getId());
            BigDecimal remaining = pickLine.getQuantityPicked().subtract(already != null ? already : BigDecimal.ZERO);
            if (req.quantity().compareTo(remaining) > 0) {
                throw new IllegalArgumentException(String.format(
                        "%s: only %s of %s picked is still unpacked",
                        item.getItemCode(), remaining.toPlainString(), pickLine.getQuantityPicked().toPlainString()));
            }
        } else if (req.inventoryItemId() != null) {
            item = pick.getLines().stream()
                    .map(PickListLine::getInventoryItem)
                    .filter(i -> i.getInventoryItemId() == req.inventoryItemId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Item " + req.inventoryItemId() + " is not on " + pick.getPickNumber() + " — name its pick line"));
        } else {
            throw new IllegalArgumentException("Name either a pick line or an item for this box line");
        }

        PackageLine line = new PackageLine();
        line.setPackageBox(box);
        line.setInventoryItem(item);
        line.setPickListLine(pickLine);
        line.setQuantity(req.quantity());

        boolean tracked = isTracked(item);
        List<Long> instanceIds = req.instanceIds() != null ? req.instanceIds() : List.of();
        if (tracked && instanceIds.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "%s is batch or serial tracked — name the instances going into this box", item.getItemCode()));
        }

        List<InventoryInstance> instances = resolveInstances(pick, item, instanceIds);
        if (tracked && totalQuantity(instances).compareTo(req.quantity()) != 0) {
            throw new IllegalArgumentException(String.format(
                    "%s: the instances named add up to %s but the packed quantity says %s",
                    item.getItemCode(), totalQuantity(instances).toPlainString(), req.quantity().toPlainString()));
        }
        for (InventoryInstance inst : instances) {
            inst.setPackageLine(line);
            inventoryInstanceRepository.save(inst);
        }

        return line;
    }

    /**
     * Every named instance must already be allocated to this pick, and not already claimed by
     * another box. Checked before anything is written so a bad line cannot half-allocate.
     */
    private List<InventoryInstance> resolveInstances(PickList pick, InventoryItem item, List<Long> ids) {
        List<InventoryInstance> resolved = new ArrayList<>();
        for (Long instanceId : ids) {
            InventoryInstance inst = inventoryInstanceRepository.findById(instanceId)
                    .orElseThrow(() -> new IllegalArgumentException("Inventory instance not found: " + instanceId));

            if (inst.getInventoryItem() == null
                    || inst.getInventoryItem().getInventoryItemId() != item.getInventoryItemId()) {
                throw new IllegalArgumentException("Instance " + instanceId + " is not " + item.getItemCode());
            }
            if (inst.getPickListLine() == null || inst.getPickListLine().getPickList() == null
                    || !inst.getPickListLine().getPickList().getId().equals(pick.getId())) {
                throw new IllegalArgumentException(String.format(
                        "Instance %d was not picked on %s", instanceId, pick.getPickNumber()));
            }
            if (inst.isConsumed() || inst.getInventoryInstanceStatus() == InventoryInstanceStatus.CONSUMED) {
                throw new IllegalArgumentException("Instance " + instanceId + " has already been consumed");
            }
            if (inst.getQualityStatus() == QualityStatus.FAILED
                    || inst.getQualityStatus() == QualityStatus.PENDING_QC) {
                throw new IllegalArgumentException(String.format(
                        "Instance %d cannot be packed: its quality status is %s",
                        instanceId, inst.getQualityStatus()));
            }
            if (inst.getPackageLine() != null) {
                throw new IllegalArgumentException(String.format(
                        "Instance %d is already packed into box %d", instanceId,
                        inst.getPackageLine().getPackageBox().getBoxNumber()));
            }
            resolved.add(inst);
        }
        return resolved;
    }

    private boolean isTracked(InventoryItem item) {
        ProductInventorySettings s = item.getProductInventorySettings();
        return s != null && (s.isBatchTracked() || s.isSerialTracked());
    }

    private BigDecimal totalQuantity(List<InventoryInstance> instances) {
        return instances.stream()
                .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {
            return "system";
        }
    }

    private PackingSlipDto toDto(PackingSlip s) {
        List<PackageBoxDto> boxes = s.getBoxes().stream()
                .filter(b -> b.getDeletedDate() == null)
                .map(this::toDto)
                .toList();
        return new PackingSlipDto(
                s.getId(), s.getSlipNumber(),
                s.getSalesOrder().getId(), s.getSalesOrder().getOrderNumber(),
                s.getPickList().getId(), s.getPickList().getPickNumber(),
                s.getStatus(), s.getPackedDate(), s.getClosedDate(),
                s.getPackedBy(), s.getClosedBy(), s.getRemarks(), s.getCreatedBy(),
                boxes);
    }

    private PackageBoxDto toDto(PackageBox b) {
        List<PackageLineDto> lines = b.getLines().stream().map(this::toDto).toList();
        return new PackageBoxDto(
                b.getId(), b.getBoxNumber(), b.getBoxType(),
                b.getLengthCm(), b.getWidthCm(), b.getHeightCm(),
                b.getGrossWeightKg(), b.getNetWeightKg(), b.getShippingMarks(),
                lines);
    }

    private PackageLineDto toDto(PackageLine l) {
        List<Long> instanceIds = inventoryInstanceRepository.findByPackageLineId(l.getId())
                .stream().map(InventoryInstance::getId).toList();
        return new PackageLineDto(
                l.getId(), l.getInventoryItem().getInventoryItemId(),
                l.getInventoryItem().getItemCode(), l.getInventoryItem().getName(),
                l.getPickListLine() != null ? l.getPickListLine().getId() : null,
                l.getQuantity(), instanceIds);
    }
}
