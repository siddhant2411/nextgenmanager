package com.nextgenmanager.nextgenmanager.marketing.enquiry.controller;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.service.EnquiryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/enquiry")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_SALES_ADMIN','ROLE_SALES_USER')")
@Validated
public class EnquiryController {

    @Autowired
    private EnquiryService enquiryService;

    private static final Logger logger = LoggerFactory.getLogger(EnquiryController.class);

    @PostMapping
    public ResponseEntity<Enquiry> createEnquiry(@RequestBody Enquiry enquiry) {
        try {
            Enquiry createEnquiry = enquiryService.createEnquiry(enquiry);
            return ResponseEntity.status(HttpStatus.CREATED).body(createEnquiry);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Enquiry> getEnquiryById(@PathVariable Long id) {
        try {
            Enquiry enquiry = enquiryService.getEnquiry(id);
            return ResponseEntity.ok(enquiry);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEnquiry(@PathVariable Long id, @RequestBody Enquiry enquiry) {
        try {
            if (id <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID: ID must be greater than zero.");
            }
            Enquiry updatedEnquiry = enquiryService.updateEnquiry(enquiry, id);
            return ResponseEntity.ok(updatedEnquiry);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllEnquiries(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "enqDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String enqNo,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) LocalDate lastContactedDate,
            @RequestParam(required = false) LocalDate enqDate,
            @RequestParam(required = false) LocalDate closedDate,
            @RequestParam(required = false) Integer daysForNextFollowup,
            @RequestParam(required = false) String lastContactedDateComp,
            @RequestParam(required = false) String enqDateComp,
            @RequestParam(required = false) String closedDateComp
    ) {
        try {
            Page<EnquiryTableDTO> allEnquiries = enquiryService.getAllActiveEnquiry(page, size, sortBy, sortDir, enqNo,
                    companyName, lastContactedDate, enqDate, closedDate, daysForNextFollowup, lastContactedDateComp, enqDateComp,
                    closedDateComp);
            return ResponseEntity.ok(allEnquiries);
        } catch (Exception e) {
            logger.error("Error while giving response for active enquiry", e);
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/enquiryWithNo")
    public ResponseEntity<?> getEnquiryWithNo(@RequestParam String enqNo) {
        try {
            Enquiry enquiry = enquiryService.getEnquiryByEnquiryNo(enqNo);
            return ResponseEntity.ok(enquiry);
        } catch (Exception e) {
            logger.error("Error while giving response for enquiry by number", e);
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/close/{id}")
    public ResponseEntity<?> closeEnquiry(@PathVariable Long id, @RequestBody String closeReason) {
        try {
            if (id <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID: ID must be greater than zero.");
            }
            enquiryService.closeEnquiry(id, closeReason);
            return ResponseEntity.status(HttpStatus.OK).body("Enquiry is now closed");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateEnquiryStatus(@PathVariable Long id, @RequestBody com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus status) {
        try {
            if (id <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID: ID must be greater than zero.");
            }
            enquiryService.updateEnquiryStatus(id, status);
            return ResponseEntity.status(HttpStatus.OK).body("Enquiry status updated");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEnquiry(@PathVariable Long id) {
        try {
            if (id <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID: ID must be greater than zero.");
            }
            enquiryService.deleteEnquiry(id);
            return ResponseEntity.status(HttpStatus.OK).body("Enquiry is now deleted");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

}
