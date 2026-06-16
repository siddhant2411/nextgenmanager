package com.nextgenmanager.nextgenmanager.marketing.enquiry.controller;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkImportResultDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkAssignRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkDeleteRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.service.EnquiryExportService;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.service.EnquiryImportService;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.service.EnquiryService;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/enquiry")
@RequiresSalesAccess
@Validated
public class EnquiryController {

    @Autowired
    private EnquiryService enquiryService;

    @Autowired
    private EnquiryExportService enquiryExportService;

    @Autowired
    private EnquiryImportService enquiryImportService;

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

    @GetMapping("/summary")
    public ResponseEntity<?> getEnquirySummary() {
        try {
            return ResponseEntity.ok(enquiryService.getEnquirySummary());
        } catch (Exception e) {
            logger.error("Error while fetching enquiry summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Failed to fetch enquiry summary: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/quotations")
    public ResponseEntity<?> getLinkedQuotations(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(enquiryService.getLinkedQuotations(id));
        } catch (Exception e) {
            logger.error("Error fetching linked quotations for enquiry {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Failed to fetch linked quotations"));
        }
    }

    @PostMapping("/convert-to-quotation/{id}")
    public ResponseEntity<?> convertToQuotation(@PathVariable Long id) {
        try {
            if (id <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID: ID must be greater than zero.");
            }
            Long quotationId = enquiryService.convertToQuotation(id);
            return ResponseEntity.status(HttpStatus.OK).body(quotationId);
        } catch (Exception e) {
            logger.error("Error while converting enquiry to quotation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Failed to convert enquiry: " + e.getMessage()));
        }
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<?> bulkDelete(@RequestBody BulkDeleteRequest request) {
        try {
            if (request.getIds() == null || request.getIds().isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "No IDs provided"));
            }
            enquiryService.bulkDelete(request);
            return ResponseEntity.ok(java.util.Map.of("message", request.getIds().size() + " enquiries deleted"));
        } catch (Exception e) {
            logger.error("Error during bulk delete", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Bulk delete failed: " + e.getMessage()));
        }
    }

    @PostMapping("/bulk-assign")
    public ResponseEntity<?> bulkAssign(@RequestBody BulkAssignRequest request) {
        try {
            if (request.getIds() == null || request.getIds().isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "No IDs provided"));
            }
            if (request.getAssignedToId() == null) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "No user ID provided"));
            }
            enquiryService.bulkAssign(request);
            return ResponseEntity.ok(java.util.Map.of("message", request.getIds().size() + " enquiries assigned"));
        } catch (Exception e) {
            logger.error("Error during bulk assign", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Bulk assign failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/bulk-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> bulkImport(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "No file provided"));
            }
            BulkImportResultDTO result = enquiryImportService.importFromFile(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error during bulk import", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Import failed: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/import-template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> getImportTemplate() {
        try {
            byte[] bytes = enquiryImportService.generateTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.attachment().filename("Enquiry_Import_Template.xlsx").build());
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating import template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportEnquiries(
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
            byte[] bytes = enquiryExportService.exportToExcel(
                    enqNo, companyName, lastContactedDate, enqDate, closedDate,
                    daysForNextFollowup, lastContactedDateComp, enqDateComp, closedDateComp);
            logger.debug("Received {} bytes from ExportService. Sending to client.", bytes != null ? bytes.length : 0);
            String filename = "Enquiry_Register_" + LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMMyyyy")) + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating enquiry Excel export", e);
            String errorJson = "{\"error\": \"Export failed: " + e.getMessage().replace("\"", "\\\"") + "\"}";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(errorJson.getBytes());
        }
    }
}
