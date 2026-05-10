package com.nextgenmanager.nextgenmanager.common.repository;

import com.nextgenmanager.nextgenmanager.common.model.DocumentChecklistItem;
import com.nextgenmanager.nextgenmanager.common.model.DocumentChecklistItem.ChecklistStatus;
import com.nextgenmanager.nextgenmanager.common.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentChecklistRepository extends JpaRepository<DocumentChecklistItem, Long> {

    List<DocumentChecklistItem> findByEntityTypeAndEntityId(String entityType, Long entityId);

    Optional<DocumentChecklistItem> findByEntityTypeAndEntityIdAndDocumentType(
            String entityType, Long entityId, DocumentType documentType);

    List<DocumentChecklistItem> findByEntityTypeAndEntityIdAndStatus(
            String entityType, Long entityId, ChecklistStatus status);
}
