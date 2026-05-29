package com.nextgenmanager.nextgenmanager.common.controller;

import com.nextgenmanager.nextgenmanager.common.dto.DocumentChecklistItemDto;
import com.nextgenmanager.nextgenmanager.common.model.DocumentChecklistItem.ChecklistStatus;
import com.nextgenmanager.nextgenmanager.common.model.DocumentType;
import com.nextgenmanager.nextgenmanager.common.service.DocumentChecklistService;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAuthenticated;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/document-checklist/{entityType}/{entityId}")
@RequiresAuthenticated
public class DocumentChecklistController {

    private final DocumentChecklistService service;

    public DocumentChecklistController(DocumentChecklistService service) {
        this.service = service;
    }

    /** GET — full checklist (lazily initialised on first call). */
    @GetMapping
    public ResponseEntity<List<DocumentChecklistItemDto>> getChecklist(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        return ResponseEntity.ok(service.getChecklist(entityType, entityId));
    }

    /** GET — list of ESSENTIAL doc types still PENDING (for soft-warn). */
    @GetMapping("/missing-essential")
    public ResponseEntity<List<DocumentType>> getMissingEssential(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        return ResponseEntity.ok(service.getMissingEssential(entityType, entityId));
    }

    /** PATCH /{itemId}/status — update status and optional notes. */
    @PatchMapping("/{itemId}/status")
    public ResponseEntity<DocumentChecklistItemDto> updateStatus(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PathVariable Long itemId,
            @RequestBody Map<String, String> body) {
        ChecklistStatus status = ChecklistStatus.valueOf(body.get("status"));
        String notes = body.get("notes");
        return ResponseEntity.ok(service.updateStatus(itemId, status, notes));
    }

    /** POST /{itemId}/upload — optional file attachment. */
    @PostMapping(value = "/{itemId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentChecklistItemDto> upload(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PathVariable Long itemId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws Exception {
        String username = auth != null ? auth.getName() : "system";
        return ResponseEntity.ok(service.attachFile(itemId, file, username));
    }

    /** DELETE /{itemId}/file — remove attached file. */
    @DeleteMapping("/{itemId}/file")
    public ResponseEntity<DocumentChecklistItemDto> removeFile(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PathVariable Long itemId) throws Exception {
        return ResponseEntity.ok(service.removeFile(itemId));
    }

    /** POST /custom — add a user-defined document entry. */
    @PostMapping("/custom")
    public ResponseEntity<DocumentChecklistItemDto> addCustomDocument(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(service.addCustomDocument(entityType, entityId, body.get("customLabel")));
    }

    /** DELETE /{itemId} — permanently remove a custom document entry. */
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteCustomDocument(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PathVariable Long itemId) {
        service.deleteCustomDocument(itemId);
        return ResponseEntity.noContent().build();
    }
}
