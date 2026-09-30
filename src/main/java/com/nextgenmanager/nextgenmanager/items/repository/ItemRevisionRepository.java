package com.nextgenmanager.nextgenmanager.items.repository;

import com.nextgenmanager.nextgenmanager.items.model.ItemRevision;
import com.nextgenmanager.nextgenmanager.items.model.ItemRevisionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemRevisionRepository extends JpaRepository<ItemRevision, Long> {

    List<ItemRevision> findByInventoryItem_InventoryItemIdOrderByCreationDateDesc(int inventoryItemId);

    Optional<ItemRevision> findByInventoryItem_InventoryItemIdAndStatus(int inventoryItemId, ItemRevisionStatus status);

    Optional<ItemRevision> findByInventoryItem_InventoryItemIdAndRevisionCode(int inventoryItemId, String revisionCode);
}
