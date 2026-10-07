package com.nextgenmanager.nextgenmanager.company.controller;

import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAdminOnly;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresAuthenticated;
import com.nextgenmanager.nextgenmanager.company.dto.BrandImageDTO;
import com.nextgenmanager.nextgenmanager.company.dto.BrandingModeRequestDTO;
import com.nextgenmanager.nextgenmanager.company.dto.DocumentBrandingDTO;
import com.nextgenmanager.nextgenmanager.company.model.BrandImageKind;
import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;
import com.nextgenmanager.nextgenmanager.company.service.DocumentBrandingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/company/branding")
@Tag(name = "Document Branding", description = "Logo and letterhead printed on generated documents")
public class DocumentBrandingController {

    @Autowired
    private DocumentBrandingService documentBrandingService;

    @GetMapping
    @Operation(summary = "Get the document branding settings")
    @RequiresAuthenticated
    public ResponseEntity<DocumentBrandingDTO> get() {
        return ResponseEntity.ok(documentBrandingService.get());
    }

    @PutMapping("/mode")
    @Operation(summary = "Choose how documents are headed (admin only)")
    @RequiresAdminOnly
    public ResponseEntity<DocumentBrandingDTO> updateMode(@Valid @RequestBody BrandingModeRequestDTO request) {
        return ResponseEntity.ok(documentBrandingService.updateMode(request.mode()));
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload the logo or the letterhead image (admin only)")
    @RequiresAdminOnly
    public ResponseEntity<DocumentBrandingDTO> uploadImage(@RequestParam BrandImageKind kind,
                                                           @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(documentBrandingService.uploadImage(kind, file));
    }

    @DeleteMapping("/image")
    @Operation(summary = "Remove the logo or the letterhead image (admin only)")
    @RequiresAdminOnly
    public ResponseEntity<DocumentBrandingDTO> removeImage(@RequestParam BrandImageKind kind) {
        return ResponseEntity.ok(documentBrandingService.removeImage(kind));
    }

    @GetMapping("/image")
    @Operation(summary = "Download the stored logo or letterhead image")
    @RequiresAuthenticated
    public ResponseEntity<byte[]> getImage(@RequestParam BrandImageKind kind) {
        BrandImageDTO image = documentBrandingService.getImage(kind);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(image.data());
    }

    @GetMapping("/preview")
    @Operation(summary = "Sample page showing the header as it prints, in the saved mode or the one given")
    @RequiresAuthenticated
    public ResponseEntity<byte[]> preview(@RequestParam(required = false) BrandingMode mode) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=branding-preview.pdf")
                .cacheControl(CacheControl.noStore())
                .body(documentBrandingService.previewPdf(mode));
    }
}
