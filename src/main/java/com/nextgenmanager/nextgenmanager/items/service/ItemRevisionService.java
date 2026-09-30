package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.items.DTO.ItemRevisionDTO;
import com.nextgenmanager.nextgenmanager.items.DTO.ItemRevisionRequestDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemRevision;

import java.util.List;

public interface ItemRevisionService {

    /**
     * Creates the item's first revision (code "A", status RELEASED) from whatever engineering
     * fields are already on it, and points {@code item.currentRevision} at it. Called once, right
     * after a new item is first saved — an item is never left without a current revision.
     */
    ItemRevision createInitialRevision(InventoryItem savedItem);

    ItemRevision getCurrentRevision(int itemId);

    List<ItemRevisionDTO> getHistory(int itemId);

    /** True when the item's current revision is RELEASED — engineering fields are frozen. */
    boolean isLocked(int itemId);

    /**
     * Opens a new DRAFT revision copying the current one's engineering fields. Fails if the
     * current revision is not RELEASED, or if a DRAFT already exists for this item.
     */
    ItemRevisionDTO reviseItem(int itemId, ItemRevisionRequestDTO request);

    /** Edits the engineering fields of a DRAFT revision. Fails once it has left DRAFT. */
    ItemRevisionDTO updateDraftRevision(long revisionId, ItemRevisionRequestDTO request);

    ItemRevisionDTO submitForApproval(long revisionId);

    /**
     * Releases a DRAFT or PENDING_APPROVAL revision: freezes it, supersedes whatever was current,
     * and syncs the item's cached {@code ProductSpecification} + uom/hsnCode so every existing
     * reader (search, export, PDF templates) keeps seeing the current engineering data without
     * having to learn about revisions.
     */
    ItemRevisionDTO release(long revisionId, ItemRevisionRequestDTO request);

    ItemRevisionDTO obsolete(long revisionId);

    /**
     * Throws IllegalArgumentException if {@code updated} changes an engineering field
     * (dimension/size/weight/basicMaterial/processType/drawingNumber/uom/hsnCode) while the
     * item's current revision is RELEASED. Called from {@code editInventoryItem} so the ordinary
     * item-save path cannot be used to sneak past the lock — the caller must go through
     * {@link #reviseItem} instead.
     */
    void assertEngineeringFieldsUnchanged(InventoryItem existing, InventoryItem updated);
}
