package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.production.dto.DispositionRequestDTO;
import com.nextgenmanager.nextgenmanager.production.enums.ReasonCodeCategory;
import com.nextgenmanager.nextgenmanager.production.model.RejectionReasonCode;
import com.nextgenmanager.nextgenmanager.production.repository.RejectionReasonCodeRepository;
import com.nextgenmanager.nextgenmanager.production.service.RejectionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/rejections")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
@Tag(name = "Rejection & Disposition", description = "MRB disposition workflow for rejected units. Allows supervisors to accept, rework, or scrap rejected production.")
public class RejectionController {

    private static final Logger logger = LoggerFactory.getLogger(RejectionController.class);

    @Autowired
    private RejectionService rejectionService;

    @Autowired
    private RejectionReasonCodeRepository rejectionReasonCodeRepository;

    /**
     * Dispose a rejection entry: ACCEPT, REWORK, or SCRAP.
     * REWORK automatically creates a child work order.
     */
    @PostMapping("/{rejectionEntryId}/dispose")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> disposeRejection(
            @PathVariable Long rejectionEntryId,
            @RequestBody DispositionRequestDTO dto) {
        try {
            dto.setRejectionEntryId(rejectionEntryId);
            rejectionService.disposeRejection(dto);
            return ResponseEntity.ok(Map.of("message", "Rejection disposed successfully",
                    "rejectionEntryId", rejectionEntryId,
                    "dispositionStatus", dto.getDispositionStatus()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error disposing rejection {}", rejectionEntryId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List all active rejection reason codes, optionally filtered by category (REJECTION, SCRAP, BOTH).
     */
    @GetMapping("/reason-codes")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<RejectionReasonCode>> listReasonCodes(
            @RequestParam(required = false) ReasonCodeCategory category) {
        List<RejectionReasonCode> codes;
        if (category != null) {
            codes = rejectionReasonCodeRepository.findByCategoryInAndIsActiveTrue(
                    List.of(category, ReasonCodeCategory.BOTH));
        } else {
            codes = rejectionReasonCodeRepository.findByIsActiveTrue();
        }
        return ResponseEntity.ok(codes);
    }
}
