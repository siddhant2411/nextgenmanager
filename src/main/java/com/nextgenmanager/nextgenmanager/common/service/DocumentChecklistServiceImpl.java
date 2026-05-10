package com.nextgenmanager.nextgenmanager.common.service;

import com.nextgenmanager.nextgenmanager.common.dto.DocumentChecklistItemDto;
import com.nextgenmanager.nextgenmanager.common.model.DocumentChecklistItem;
import com.nextgenmanager.nextgenmanager.common.model.DocumentChecklistItem.ChecklistStatus;
import com.nextgenmanager.nextgenmanager.common.model.DocumentType;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.repository.DocumentChecklistRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
@Transactional
public class DocumentChecklistServiceImpl implements DocumentChecklistService {

    // ── Template: which doc types belong to each entity type ─────────────────
    private static final Map<String, List<DocumentType>> TEMPLATES;
    static {
        TEMPLATES = new LinkedHashMap<>();
        TEMPLATES.put("PURCHASE_ORDER", List.of(
                DocumentType.VENDOR_QUOTATION,
                DocumentType.ORDER_ACKNOWLEDGMENT,
                DocumentType.DELIVERY_CHALLAN,
                DocumentType.INVOICE,
                DocumentType.GRN,
                DocumentType.EWAY_BILL,
                DocumentType.PACKING_LIST,
                DocumentType.TEST_CERTIFICATE,
                DocumentType.REJECTION_NOTE
        ));
        TEMPLATES.put("VENDOR_BILL", List.of(
                DocumentType.VENDOR_BILL,
                DocumentType.PAYMENT_VOUCHER,
                DocumentType.DEBIT_NOTE,
                DocumentType.CREDIT_NOTE,
                DocumentType.TDS_CERTIFICATE
        ));
    }

    private final DocumentChecklistRepository repo;
    private final FileStorageService fileStorage;

    public DocumentChecklistServiceImpl(DocumentChecklistRepository repo,
                                        FileStorageService fileStorage) {
        this.repo        = repo;
        this.fileStorage = fileStorage;
    }

    @Override
    public List<DocumentChecklistItemDto> getChecklist(String entityType, Long entityId) {
        List<DocumentChecklistItem> existing = repo.findByEntityTypeAndEntityId(entityType, entityId);
        existing = ensureInitialised(entityType, entityId, existing);
        return existing.stream().map(this::toDto).toList();
    }

    @Override
    public DocumentChecklistItemDto updateStatus(Long itemId, ChecklistStatus status, String notes) {
        DocumentChecklistItem item = find(itemId);
        item.setStatus(status);
        if (notes != null) item.setNotes(notes);
        if (status == ChecklistStatus.RECEIVED && item.getReceivedDate() == null) {
            item.setReceivedDate(new Date());
        }
        return toDto(repo.save(item));
    }

    @Override
    public DocumentChecklistItemDto attachFile(Long itemId, MultipartFile file, String uploadedBy) throws Exception {
        DocumentChecklistItem item = find(itemId);
        String actor = uploadedBy != null ? uploadedBy : currentUsername();

        String fileName = fileStorage.uploadFile(
                file,
                "document-checklist",
                "DOCUMENT_CHECKLIST",
                itemId,
                actor
        );

        // Fetch the saved attachment to get its id and presigned URL
        List<FileAttachment> attachments = fileStorage.findAttachmentsByTypeAndId("DOCUMENT_CHECKLIST", itemId);
        FileAttachment saved = attachments.stream()
                .filter(a -> a.getFileName().equals(fileName))
                .findFirst()
                .orElseThrow();

        item.setFileAttachmentId(saved.getId());
        item.setFileName(saved.getOriginalName());
        item.setFileUrl(saved.getPresignedUrl());
        if (item.getStatus() == ChecklistStatus.PENDING) {
            item.setStatus(ChecklistStatus.RECEIVED);
            item.setReceivedDate(new Date());
        }
        return toDto(repo.save(item));
    }

    @Override
    public DocumentChecklistItemDto removeFile(Long itemId) throws Exception {
        DocumentChecklistItem item = find(itemId);
        if (item.getFileAttachmentId() != null) {
            fileStorage.deleteAttachment(item.getFileAttachmentId());
        }
        item.setFileAttachmentId(null);
        item.setFileName(null);
        item.setFileUrl(null);
        return toDto(repo.save(item));
    }

    @Override
    public List<DocumentType> getMissingEssential(String entityType, Long entityId) {
        List<DocumentChecklistItem> items = repo.findByEntityTypeAndEntityId(entityType, entityId);
        items = ensureInitialised(entityType, entityId, items);
        return items.stream()
                .filter(i -> i.getDocumentType().tier == DocumentType.Tier.ESSENTIAL)
                .filter(i -> i.getStatus() == ChecklistStatus.PENDING)
                .map(DocumentChecklistItem::getDocumentType)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<DocumentChecklistItem> ensureInitialised(String entityType, Long entityId,
                                                           List<DocumentChecklistItem> existing) {
        List<DocumentType> template = TEMPLATES.getOrDefault(entityType, List.of());
        if (template.isEmpty()) return existing;

        Set<DocumentType> present = new HashSet<>();
        for (DocumentChecklistItem i : existing) present.add(i.getDocumentType());

        List<DocumentChecklistItem> toCreate = new ArrayList<>();
        for (DocumentType dt : template) {
            if (!present.contains(dt)) {
                DocumentChecklistItem item = new DocumentChecklistItem();
                item.setEntityType(entityType);
                item.setEntityId(entityId);
                item.setDocumentType(dt);
                item.setStatus(ChecklistStatus.PENDING);
                toCreate.add(item);
            }
        }
        if (!toCreate.isEmpty()) {
            List<DocumentChecklistItem> saved = repo.saveAll(toCreate);
            List<DocumentChecklistItem> merged = new ArrayList<>(existing);
            merged.addAll(saved);
            // Return in template order
            merged.sort(Comparator.comparingInt(i -> template.indexOf(i.getDocumentType())));
            return merged;
        }
        existing.sort(Comparator.comparingInt(i -> template.indexOf(i.getDocumentType())));
        return existing;
    }

    private DocumentChecklistItem find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Checklist item not found: " + id));
    }

    @Override
    public DocumentChecklistItemDto addCustomDocument(String entityType, Long entityId, String customLabel) {
        if (customLabel == null || customLabel.isBlank())
            throw new IllegalArgumentException("Custom document label must not be blank");
        DocumentChecklistItem item = new DocumentChecklistItem();
        item.setEntityType(entityType);
        item.setEntityId(entityId);
        item.setDocumentType(DocumentType.CUSTOM);
        item.setCustomLabel(customLabel.trim());
        item.setStatus(DocumentChecklistItem.ChecklistStatus.PENDING);
        return toDto(repo.save(item));
    }

    @Override
    public void deleteCustomDocument(Long itemId) {
        DocumentChecklistItem item = find(itemId);
        if (item.getDocumentType() != DocumentType.CUSTOM)
            throw new IllegalArgumentException("Only custom documents can be deleted");
        repo.delete(item);
    }

    private DocumentChecklistItemDto toDto(DocumentChecklistItem i) {
        String label = i.getDocumentType() == DocumentType.CUSTOM && i.getCustomLabel() != null
                ? i.getCustomLabel()
                : i.getDocumentType().label;
        return new DocumentChecklistItemDto(
                i.getId(),
                i.getEntityType(),
                i.getEntityId(),
                i.getDocumentType(),
                label,
                i.getDocumentType().tier.name(),
                i.getStatus(),
                i.getFileAttachmentId(),
                i.getFileName(),
                i.getFileUrl(),
                i.getReceivedDate(),
                i.getNotes(),
                i.getUpdatedDate(),
                i.getCustomLabel()
        );
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
