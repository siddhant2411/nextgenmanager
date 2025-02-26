package com.nextgenmanager.nextgenmanager.items.repository;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItemAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryItemAttachmentRepository extends JpaRepository<InventoryItemAttachment,Long> {
}
