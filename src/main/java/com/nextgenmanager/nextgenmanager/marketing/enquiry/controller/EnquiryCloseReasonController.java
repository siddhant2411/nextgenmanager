package com.nextgenmanager.nextgenmanager.marketing.enquiry.controller;

import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryCloseReasonDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseReason;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryCloseReasonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enquiry-close-reason")
@RequiresSalesAccess
public class EnquiryCloseReasonController {

    private static final Logger logger = LoggerFactory.getLogger(EnquiryCloseReasonController.class);

    @Autowired
    private EnquiryCloseReasonRepository repository;

    /** Active reasons only — this is what the close-enquiry dropdown binds to. */
    @GetMapping
    public ResponseEntity<List<EnquiryCloseReasonDTO>> getActive() {
        return ResponseEntity.ok(repository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream().map(this::toDto).toList());
    }

    /** Includes deactivated reasons, so historic enquiries can still be explained. */
    @GetMapping("/all")
    public ResponseEntity<List<EnquiryCloseReasonDTO>> getAll() {
        return ResponseEntity.ok(repository.findAll().stream().map(this::toDto).toList());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody EnquiryCloseReasonDTO request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code is required"));
        }
        String code = request.getCode().trim().toUpperCase();
        if (repository.findByCodeIgnoreCase(code).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "code already exists: " + code));
        }
        EnquiryCloseReason entity = new EnquiryCloseReason();
        entity.setCode(code);
        entity.setDescription(request.getDescription());
        if (request.getOutcome() != null) entity.setOutcome(request.getOutcome());
        if (request.getDisplayOrder() != null) entity.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsActive() != null) entity.setIsActive(request.getIsActive());
        logger.info("Creating enquiry close reason {}", code);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(repository.save(entity)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody EnquiryCloseReasonDTO request) {
        return repository.findById(id).map(entity -> {
            if (request.getDescription() != null) entity.setDescription(request.getDescription());
            if (request.getOutcome() != null) entity.setOutcome(request.getOutcome());
            if (request.getDisplayOrder() != null) entity.setDisplayOrder(request.getDisplayOrder());
            if (request.getIsActive() != null) entity.setIsActive(request.getIsActive());
            return ResponseEntity.ok(toDto(repository.save(entity)));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Deactivates rather than deletes — enquiries closed under this reason keep pointing at it,
     * so last year's numbers do not change when sales tidies up the dropdown.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        return repository.findById(id).map(entity -> {
            entity.setIsActive(false);
            repository.save(entity);
            return ResponseEntity.ok(Map.of("message", "Close reason deactivated"));
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private EnquiryCloseReasonDTO toDto(EnquiryCloseReason e) {
        return EnquiryCloseReasonDTO.builder()
                .id(e.getId())
                .code(e.getCode())
                .description(e.getDescription())
                .outcome(e.getOutcome())
                .displayOrder(e.getDisplayOrder())
                .isActive(e.getIsActive())
                .build();
    }
}
