package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.dto.DocumentChecklistItemDto;
import com.nextgenmanager.nextgenmanager.common.model.DocumentChecklistItem.ChecklistStatus;
import com.nextgenmanager.nextgenmanager.common.model.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentChecklistService {

    /** Returns (and lazily initialises) the full checklist for an entity. */
    List<DocumentChecklistItemDto> getChecklist(String entityType, Long entityId);

    /** Transition status of a single checklist row. */
    DocumentChecklistItemDto updateStatus(Long itemId, ChecklistStatus status, String notes);

    /** Attach a file to a checklist row (optional — upload not mandatory). */
    DocumentChecklistItemDto attachFile(Long itemId, MultipartFile file, String uploadedBy) throws Exception;

    /** Remove the attached file from a checklist row. */
    DocumentChecklistItemDto removeFile(Long itemId) throws Exception;

    /**
     * Returns ESSENTIAL document types that are still PENDING for the given entity.
     * Used for soft-warn dialogs before close/complete actions.
     */
    List<DocumentType> getMissingEssential(String entityType, Long entityId);

    /** Add a user-defined custom document entry to the checklist. */
    DocumentChecklistItemDto addCustomDocument(String entityType, Long entityId, String customLabel);

    /** Permanently remove a custom document entry from the checklist. */
    void deleteCustomDocument(Long itemId);
}
