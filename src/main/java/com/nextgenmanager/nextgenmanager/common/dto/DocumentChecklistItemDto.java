package com.nextgenmanager.nextgenmanager.common.dto;

import com.nextgenmanager.nextgenmanager.common.model.DocumentChecklistItem.ChecklistStatus;
import com.nextgenmanager.nextgenmanager.common.model.DocumentType;

import java.util.Date;

public record DocumentChecklistItemDto(
        Long id,
        String entityType,
        Long entityId,
        DocumentType documentType,
        String documentTypeLabel,
        String tier,
        ChecklistStatus status,
        Long fileAttachmentId,
        String fileName,
        String fileUrl,
        Date receivedDate,
        String notes,
        Date updatedDate,
        String customLabel
) {}
