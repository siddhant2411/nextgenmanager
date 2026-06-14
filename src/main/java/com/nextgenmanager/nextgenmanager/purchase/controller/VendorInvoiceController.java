package com.nextgenmanager.nextgenmanager.purchase.controller;

import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.purchase.dto.CreateVendorInvoiceRequest;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceDto;
import com.nextgenmanager.nextgenmanager.purchase.dto.VendorInvoiceListDto;
import com.nextgenmanager.nextgenmanager.purchase.service.VendorInvoiceService;
import io.minio.GetObjectResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.nextgenmanager.nextgenmanager.common.security.authorization.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/vendor-invoices")
@RequiresPurchaseAccess
public class VendorInvoiceController {

    private final VendorInvoiceService service;

    public VendorInvoiceController(VendorInvoiceService service) {
        this.service = service;
    }

    @PostMapping
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<VendorInvoiceDto> create(@RequestBody CreateVendorInvoiceRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendorInvoiceDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<VendorInvoiceListDto>> getByPo(@RequestParam Long poId) {
        return ResponseEntity.ok(service.getByPo(poId));
    }

    @PutMapping("/{id}/post")
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<VendorInvoiceDto> post(@PathVariable Long id) {
        return ResponseEntity.ok(service.post(id));
    }

    @PutMapping("/{id}/cancel")
    @RequiresAdminOnly
    public ResponseEntity<VendorInvoiceDto> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    // ── Attachments ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/attachments")
    public ResponseEntity<List<FileAttachment>> getAttachments(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAttachments(id));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<String> uploadAttachment(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file) throws Exception {
        service.uploadAttachment(id, file);
        return ResponseEntity.ok("Attachment uploaded successfully.");
    }

    @DeleteMapping("/{id}/attachments/{fileId}")
    @RequiresPurchaseInventoryAdminAccess
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long id,
                                                 @PathVariable Long fileId) throws Exception {
        service.deleteAttachment(fileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/attachments/{fileId}/download")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id,
                                                     @PathVariable Long fileId) throws Exception {
        FileAttachment meta = service.getAttachment(fileId);
        GetObjectResponse stream = service.downloadAttachment(fileId);
        byte[] bytes = stream.readAllBytes();
        MediaType mediaType = (meta != null && meta.getContentType() != null)
                ? MediaType.parseMediaType(meta.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        String filename = meta != null && meta.getOriginalName() != null ? meta.getOriginalName() : "attachment";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(bytes);
    }
}
