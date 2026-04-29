package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingOperationDto;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.model.Routing;
import com.nextgenmanager.nextgenmanager.production.service.RoutingService;
import io.minio.GetObjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.xml.bind.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/manufacturing/routing")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Routing", description = "Manufacturing routing and operation management with attachments")
public class RoutingController {

    private final RoutingService routingService;

    @Autowired
    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    // ---------------------------------------------------------------------------
    // CREATE or UPDATE Routing (for a BOM)
    // ---------------------------------------------------------------------------
    @PostMapping("/bom/{bomId}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @Operation(summary = "Create or update routing for a BOM", description = "Creates a new routing or updates existing one including all operations and dependencies")
    public ResponseEntity<RoutingDto> createOrUpdateRouting(
            @PathVariable Integer bomId,
            @RequestBody RoutingDto routingDto,
            @Parameter(description = "User performing the action") @RequestHeader("X-Actor") String actor) {

        routingDto.setBomId(bomId);

        RoutingDto routing = routingService.createOrUpdateRouting(bomId, routingDto, actor);

        return ResponseEntity.ok(routing);
    }

    // ---------------------------------------------------------------------------
    // UPDATE Operations Only
    // ---------------------------------------------------------------------------
    @PutMapping("/{routingId}/operations")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @Operation(summary = "Update operations for a routing", description = "Updates operation list for an existing DRAFT/APPROVED routing")
    public ResponseEntity<Routing> updateOperations(
            @PathVariable Long routingId,
            @RequestBody List<RoutingOperationDto> operations,
            @Parameter(description = "User performing the action") @RequestHeader("X-Actor") String actor) {

        Routing updated = routingService.updateOperations(routingId, operations, actor);

        return ResponseEntity.ok(updated);
    }

    // ---------------------------------------------------------------------------
    // APPROVE Routing
    // ---------------------------------------------------------------------------
    @PostMapping("/{routingId}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @Operation(summary = "Approve a DRAFT routing")
    public ResponseEntity<Void> approve(
            @PathVariable Long routingId,
            @Parameter(description = "User performing the action") @RequestHeader("X-Actor") String actor) throws ValidationException {

        routingService.approve(routingId, actor);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------------
    // ACTIVATE Routing
    // ---------------------------------------------------------------------------
    @PostMapping("/{routingId}/activate")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @Operation(summary = "Activate an APPROVED routing")
    public ResponseEntity<Void> activate(
            @PathVariable Long routingId,
            @Parameter(description = "User performing the action") @RequestHeader("X-Actor") String actor) throws ValidationException {

        routingService.activate(routingId, actor);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------------
    // OBSOLETE Routing
    // ---------------------------------------------------------------------------
    @PostMapping("/{routingId}/obsolete")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @Operation(summary = "Mark a routing as obsolete")
    public ResponseEntity<Void> obsolete(
            @PathVariable Long routingId,
            @Parameter(description = "User performing the action") @RequestHeader("X-Actor") String actor) {

        routingService.obsolete(routingId, actor);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------------------
    // GET Routing for BOM
    // ---------------------------------------------------------------------------
    @GetMapping("/bom/{bomId}")
    @Operation(summary = "Get routing by BOM ID", description = "Returns the routing with all operations and their attachments")
    public ResponseEntity<RoutingDto> getByBom(@PathVariable Integer bomId) {
        RoutingDto routing = routingService.getByBom(bomId);
        return ResponseEntity.ok(routing);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get routing by ID")
    public ResponseEntity<RoutingDto> getRouting(@PathVariable Long id) {
        RoutingDto routing = routingService.getRouting(id);
        return ResponseEntity.ok(routing);
    }

    // ---------------------------------------------------------------------------
    // GET all operations for a routing
    // ---------------------------------------------------------------------------
    @GetMapping("/{routingId}/operations")
    @Operation(summary = "List all operations for a routing", description = "Returns operations with dependencies and file attachments")
    public ResponseEntity<List<RoutingOperationDto>> getOperations(
            @PathVariable Long routingId) {

        return ResponseEntity.ok(routingService.getOperations(routingId));
    }

    // ---------------------------------------------------------------------------
    // OPERATION ATTACHMENTS
    // ---------------------------------------------------------------------------

    @PostMapping(value = "/operation/{operationId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @Operation(summary = "Upload attachment for a routing operation", description = "Attach drawings, SOPs or other documents to a specific operation. Routing must be in DRAFT or APPROVED status.")
    public ResponseEntity<?> uploadOperationAttachment(
            @PathVariable Long operationId,
            @Parameter(description = "File to upload") @RequestPart MultipartFile file) throws Exception {

        routingService.uploadOperationAttachment(operationId, file);
        return ResponseEntity.ok("File uploaded successfully");
    }

    @GetMapping("/operation/{operationId}/attachments")
    @Operation(summary = "List attachments for a routing operation")
    public ResponseEntity<List<FileAttachment>> getOperationAttachments(
            @PathVariable Long operationId) {

        return ResponseEntity.ok(routingService.getOperationAttachments(operationId));
    }

    @DeleteMapping("/operation/{operationId}/delete-attachment/{fileId}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    @Operation(summary = "Delete an attachment from a routing operation", description = "Routing must be in DRAFT or APPROVED status.")
    public ResponseEntity<?> deleteOperationAttachment(
            @PathVariable Long operationId,
            @PathVariable Long fileId) throws Exception {

        routingService.deleteOperationAttachment(operationId, fileId);
        return ResponseEntity.ok("File deleted successfully");
    }

    @GetMapping("/operation/download/{fileId}")
    @Operation(summary = "Download a routing operation attachment")
    public ResponseEntity<byte[]> downloadOperationAttachment(
            @PathVariable Long fileId) throws Exception {

        GetObjectResponse file = routingService.downloadOperationAttachment(fileId);
        FileAttachment attachment = routingService.getOperationAttachment(fileId);

        byte[] fileBytes = file.readAllBytes();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        attachment.getContentType() != null
                                ? attachment.getContentType()
                                : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getOriginalName() + "\"")
                .body(fileBytes);
    }
}
