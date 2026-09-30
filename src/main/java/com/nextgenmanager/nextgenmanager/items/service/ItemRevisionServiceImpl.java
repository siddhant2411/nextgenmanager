package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.DTO.ItemRevisionDTO;
import com.nextgenmanager.nextgenmanager.items.DTO.ItemRevisionRequestDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemRevision;
import com.nextgenmanager.nextgenmanager.items.model.ItemRevisionStatus;
import com.nextgenmanager.nextgenmanager.items.model.ProductSpecification;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ItemRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * See {@code docs/ITEM_REVISION_CONTROL_PLAN.md} for the design this implements.
 */
@Service
@RequiredArgsConstructor
public class ItemRevisionServiceImpl implements ItemRevisionService {

    private static final Logger logger = LoggerFactory.getLogger(ItemRevisionServiceImpl.class);

    private final ItemRevisionRepository itemRevisionRepository;
    private final InventoryItemRepository inventoryItemRepository;

    /** Open/edit/submit a draft — engineering's own workflow. */
    private boolean canDraftRevisions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                a.getAuthority().equals("ROLE_ADMIN") ||
                a.getAuthority().equals("ROLE_INVENTORY_ADMIN") ||
                a.getAuthority().equals("ROLE_ENGINEERING"));
    }

    /** Release/obsolete — the act that locks or unlocks the part, kept to admin roles. */
    private boolean canReleaseRevisions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                a.getAuthority().equals("ROLE_ADMIN") ||
                a.getAuthority().equals("ROLE_INVENTORY_ADMIN"));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private void requireDraftRole() {
        if (!canDraftRevisions()) {
            throw new IllegalArgumentException("Not permitted to manage item revisions.");
        }
    }

    private void requireReleaseRole() {
        if (!canReleaseRevisions()) {
            throw new IllegalArgumentException("Not permitted to release item revisions.");
        }
    }

    /** A, B, C ... Z, AA, AB ... — same scheme as BomServiceImpl.toRevision/nextRevision. */
    private static String nextRevisionCode(String current) {
        int num = 0;
        for (int i = 0; i < current.length(); i++) {
            num = num * 26 + (current.charAt(i) - 'A' + 1);
        }
        num++;
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            num--;
            sb.append((char) ('A' + (num % 26)));
            num /= 26;
        }
        return sb.reverse().toString();
    }

    private ItemRevision findOr404(long revisionId) {
        return itemRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Item revision not found: " + revisionId));
    }

    private void copyEngineeringFields(ItemRevision from, ItemRevision to) {
        to.setDimension(from.getDimension());
        to.setSize(from.getSize());
        to.setWeight(from.getWeight());
        to.setBasicMaterial(from.getBasicMaterial());
        to.setProcessType(from.getProcessType());
        to.setDrawingNumber(from.getDrawingNumber());
        to.setUom(from.getUom());
        to.setHsnCode(from.getHsnCode());
    }

    private void applyRequestedEngineeringFields(ItemRevision rev, ItemRevisionRequestDTO req) {
        if (req.getDimension() != null) rev.setDimension(req.getDimension());
        if (req.getSize() != null) rev.setSize(req.getSize());
        if (req.getWeight() != null) rev.setWeight(req.getWeight());
        if (req.getBasicMaterial() != null) rev.setBasicMaterial(req.getBasicMaterial());
        if (req.getProcessType() != null) rev.setProcessType(req.getProcessType());
        if (req.getDrawingNumber() != null) rev.setDrawingNumber(req.getDrawingNumber());
        if (req.getUom() != null) rev.setUom(req.getUom());
        if (req.getHsnCode() != null) rev.setHsnCode(req.getHsnCode());
    }

    @Override
    @Transactional
    public ItemRevision createInitialRevision(InventoryItem savedItem) {
        ItemRevision rev = new ItemRevision();
        rev.setInventoryItem(savedItem);
        rev.setRevisionCode("A");
        rev.setStatus(ItemRevisionStatus.RELEASED);
        rev.setReleasedBy(currentUsername());
        rev.setReleasedOn(savedItem.getCreationDate() != null ? savedItem.getCreationDate() : new Date());
        rev.setEffectiveFrom(rev.getReleasedOn());

        ProductSpecification spec = savedItem.getProductSpecification();
        if (spec != null) {
            rev.setDimension(spec.getDimension());
            rev.setSize(spec.getSize());
            rev.setWeight(spec.getWeight());
            rev.setBasicMaterial(spec.getBasicMaterial());
            rev.setProcessType(spec.getProcessType());
            rev.setDrawingNumber(spec.getDrawingNumber());
        }
        rev.setUom(savedItem.getUom());
        rev.setHsnCode(savedItem.getHsnCode());

        ItemRevision saved = itemRevisionRepository.save(rev);
        savedItem.setCurrentRevision(saved);
        inventoryItemRepository.save(savedItem);
        logger.info("Item {} created at revision A (released)", savedItem.getInventoryItemId());
        return saved;
    }

    @Override
    public ItemRevision getCurrentRevision(int itemId) {
        InventoryItem item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item does not exist: " + itemId));
        return item.getCurrentRevision();
    }

    @Override
    public List<ItemRevisionDTO> getHistory(int itemId) {
        return itemRevisionRepository.findByInventoryItem_InventoryItemIdOrderByCreationDateDesc(itemId)
                .stream().map(ItemRevisionMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public boolean isLocked(int itemId) {
        ItemRevision current = getCurrentRevision(itemId);
        return current != null && current.getStatus() == ItemRevisionStatus.RELEASED;
    }

    @Override
    @Transactional
    public ItemRevisionDTO reviseItem(int itemId, ItemRevisionRequestDTO request) {
        requireDraftRole();
        InventoryItem item = inventoryItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item does not exist: " + itemId));

        ItemRevision current = item.getCurrentRevision();
        if (current == null) {
            throw new IllegalArgumentException("Item has no current revision to revise from.");
        }
        if (current.getStatus() != ItemRevisionStatus.RELEASED) {
            throw new IllegalArgumentException(
                    "Current revision " + current.getRevisionCode() + " is " + current.getStatus() +
                    ", not RELEASED — it is already open for change.");
        }

        boolean draftExists = itemRevisionRepository
                .findByInventoryItem_InventoryItemIdAndStatus(itemId, ItemRevisionStatus.DRAFT)
                .isPresent();
        if (draftExists) {
            throw new IllegalArgumentException("A draft revision already exists for this item.");
        }

        ItemRevision draft = new ItemRevision();
        draft.setInventoryItem(item);
        draft.setRevisionCode(nextRevisionCode(current.getRevisionCode()));
        draft.setStatus(ItemRevisionStatus.DRAFT);
        copyEngineeringFields(current, draft);
        draft.setChangeReason(request != null ? request.getChangeReason() : null);
        draft.setEcoNumber(request != null ? request.getEcoNumber() : null);

        ItemRevision saved = itemRevisionRepository.save(draft);
        logger.info("Item {} revision {} opened (draft, from released {})",
                itemId, saved.getRevisionCode(), current.getRevisionCode());
        return ItemRevisionMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ItemRevisionDTO updateDraftRevision(long revisionId, ItemRevisionRequestDTO request) {
        requireDraftRole();
        ItemRevision rev = findOr404(revisionId);
        if (rev.getStatus() != ItemRevisionStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Revision " + rev.getRevisionCode() + " is " + rev.getStatus() + " — only a DRAFT can be edited.");
        }
        applyRequestedEngineeringFields(rev, request);
        if (request.getChangeReason() != null) rev.setChangeReason(request.getChangeReason());
        if (request.getEcoNumber() != null) rev.setEcoNumber(request.getEcoNumber());
        return ItemRevisionMapper.toDto(itemRevisionRepository.save(rev));
    }

    @Override
    @Transactional
    public ItemRevisionDTO submitForApproval(long revisionId) {
        requireDraftRole();
        ItemRevision rev = findOr404(revisionId);
        if (rev.getStatus() != ItemRevisionStatus.DRAFT) {
            throw new IllegalArgumentException(
                    "Only a DRAFT revision can be submitted for approval (current: " + rev.getStatus() + ").");
        }
        rev.setStatus(ItemRevisionStatus.PENDING_APPROVAL);
        return ItemRevisionMapper.toDto(itemRevisionRepository.save(rev));
    }

    @Override
    @Transactional
    public ItemRevisionDTO release(long revisionId, ItemRevisionRequestDTO request) {
        requireReleaseRole();
        ItemRevision rev = findOr404(revisionId);
        if (rev.getStatus() != ItemRevisionStatus.DRAFT && rev.getStatus() != ItemRevisionStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException(
                    "Only a DRAFT or PENDING_APPROVAL revision can be released (current: " + rev.getStatus() + ").");
        }
        if (request != null) {
            applyRequestedEngineeringFields(rev, request);
            if (request.getChangeReason() != null) rev.setChangeReason(request.getChangeReason());
            if (request.getEcoNumber() != null) rev.setEcoNumber(request.getEcoNumber());
        }

        InventoryItem item = rev.getInventoryItem();
        ItemRevision previousCurrent = item.getCurrentRevision();

        rev.setStatus(ItemRevisionStatus.RELEASED);
        rev.setReleasedBy(currentUsername());
        rev.setReleasedOn(new Date());
        rev.setEffectiveFrom(rev.getReleasedOn());
        rev.setInterchangeable(request != null ? request.getInterchangeable() : null);
        ItemRevision saved = itemRevisionRepository.save(rev);

        if (previousCurrent != null && !Objects.equals(previousCurrent.getId(), saved.getId())) {
            previousCurrent.setStatus(ItemRevisionStatus.SUPERSEDED);
            previousCurrent.setSupersededBy(saved);
            previousCurrent.setEffectiveTo(saved.getReleasedOn());
            itemRevisionRepository.save(previousCurrent);
        }

        // Sync the item's cached engineering fields so every existing reader (search, export, PDF
        // templates) sees the newly-released data without having to learn about revisions.
        item.setCurrentRevision(saved);
        item.setUom(saved.getUom());
        item.setHsnCode(saved.getHsnCode());
        ProductSpecification spec = item.getProductSpecification();
        if (spec == null) {
            spec = new ProductSpecification();
            spec.setInventoryItem(item);
            item.setProductSpecification(spec);
        }
        spec.setDimension(saved.getDimension());
        spec.setSize(saved.getSize());
        spec.setWeight(saved.getWeight());
        spec.setBasicMaterial(saved.getBasicMaterial());
        spec.setProcessType(saved.getProcessType());
        spec.setDrawingNumber(saved.getDrawingNumber());
        inventoryItemRepository.save(item);

        logger.info("Item {} revision {} released ({})", item.getInventoryItemId(), saved.getRevisionCode(),
                saved.getInterchangeable() == null ? "interchangeability not recorded"
                        : saved.getInterchangeable() ? "interchangeable with prior stock" : "NOT interchangeable — impact review needed");
        return ItemRevisionMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ItemRevisionDTO obsolete(long revisionId) {
        requireReleaseRole();
        ItemRevision rev = findOr404(revisionId);
        if (rev.getStatus() != ItemRevisionStatus.RELEASED && rev.getStatus() != ItemRevisionStatus.SUPERSEDED) {
            throw new IllegalArgumentException(
                    "Only a RELEASED or SUPERSEDED revision can be marked obsolete (current: " + rev.getStatus() + ").");
        }
        rev.setStatus(ItemRevisionStatus.OBSOLETE);
        return ItemRevisionMapper.toDto(itemRevisionRepository.save(rev));
    }

    @Override
    public void assertEngineeringFieldsUnchanged(InventoryItem existing, InventoryItem updated) {
        ItemRevision current = existing.getCurrentRevision();
        if (current == null || current.getStatus() != ItemRevisionStatus.RELEASED) {
            return; // no current revision (legacy row) or not locked — nothing to enforce yet
        }

        ProductSpecification oldSpec = existing.getProductSpecification();
        ProductSpecification newSpec = updated.getProductSpecification();

        boolean changed =
                !Objects.equals(existing.getUom(), updated.getUom()) ||
                !Objects.equals(existing.getHsnCode(), updated.getHsnCode()) ||
                !Objects.equals(fld(oldSpec, ProductSpecification::getDimension), fld(newSpec, ProductSpecification::getDimension)) ||
                !Objects.equals(fld(oldSpec, ProductSpecification::getSize), fld(newSpec, ProductSpecification::getSize)) ||
                !Objects.equals(fld(oldSpec, ProductSpecification::getWeight), fld(newSpec, ProductSpecification::getWeight)) ||
                !Objects.equals(fld(oldSpec, ProductSpecification::getBasicMaterial), fld(newSpec, ProductSpecification::getBasicMaterial)) ||
                !Objects.equals(fld(oldSpec, ProductSpecification::getProcessType), fld(newSpec, ProductSpecification::getProcessType)) ||
                !Objects.equals(fld(oldSpec, ProductSpecification::getDrawingNumber), fld(newSpec, ProductSpecification::getDrawingNumber));

        if (changed) {
            throw new IllegalArgumentException(
                    "Item is locked at revision " + current.getRevisionCode() +
                    " — engineering fields (dimension, size, weight, material, process, drawing number, UoM, HSN) " +
                    "cannot be changed. Use Revise to open a new draft revision.");
        }
    }

    private static String fld(ProductSpecification spec, java.util.function.Function<ProductSpecification, String> getter) {
        return spec == null ? null : getter.apply(spec);
    }
}
