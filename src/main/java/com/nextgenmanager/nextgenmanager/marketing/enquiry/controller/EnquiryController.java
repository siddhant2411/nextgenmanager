package com.nextgenmanager.nextgenmanager.marketing.enquiry.controller;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkImportResultDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryConversationDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.CrmPeriod;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.AiReviewDecisionDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryFilter;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkAssignRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkDeleteRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.service.EnquiryExportService;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.service.EnquiryImportService;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.service.CrmAnalyticsService;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.service.EnquiryService;
import org.springframework.web.multipart.MultipartFile;
import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

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

    @Autowired
    private CrmAnalyticsService crmAnalyticsService;

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

    /**
     * Rejects any query parameter the filter does not understand.
     *
     * Spring binds the parameters it recognises and discards the rest without a word, so
     * `?status=CLOSED` used to return all 329 enquiries -- a dashboard tile would have shown a
     * wrong number under a correct-looking label, which is worse than an error. A typo now
     * comes back as a 400 naming the parameter.
     */
    private static void rejectUnknownParams(HttpServletRequest request) {
        rejectUnknownParams(request, EnquiryFilter.KNOWN_PARAMS);
    }

    private static void rejectUnknownParams(HttpServletRequest request, java.util.Set<String> allowed) {
        List<String> unknown = request.getParameterMap().keySet().stream()
                .filter(name -> !allowed.contains(name))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new InvalidDataException("Unknown query parameter(s): " + String.join(", ", unknown)
                    + ". Supported: " + allowed.stream().sorted().collect(Collectors.joining(", ")));
        }
    }

    /**
     * The dashboard endpoints take a window and nothing else.
     *
     * <p>They get the same strict treatment as the list for the same reason: a mistyped
     * {@code ?form=2026-04-01} would bind nothing, silently fall back to the default period, and
     * render this month's figures under a label claiming the year. A wrong number beneath a
     * correct-looking label is worse than an error.
     */
    private static final java.util.Set<String> CRM_PARAMS = java.util.Set.of("preset", "from", "to");

    @GetMapping
    public ResponseEntity<?> getAllEnquiries(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(200) int size,
            @RequestParam(defaultValue = "enqDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @ModelAttribute EnquiryFilter filter,
            HttpServletRequest request
    ) {
        rejectUnknownParams(request);
        Page<EnquiryTableDTO> allEnquiries = enquiryService.getAllActiveEnquiry(page, size, sortBy, sortDir, filter);
        return ResponseEntity.ok(allEnquiries);
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

    /**
     * Human verdict on an enquiry the AI Lead Agent raised.
     *
     * The agent holds low-confidence extractions in its own queue and never writes them here, but
     * anything it did write above the review threshold lands with aiRequiresReview set. This is
     * how that flag clears -- from the CRM, by a person, with the reason logged.
     */
    @PostMapping("/{id}/ai-review")
    public ResponseEntity<?> applyAiReview(@PathVariable Long id,
                                           @RequestBody AiReviewDecisionDTO decision) {
        return ResponseEntity.ok(enquiryService.applyAiReview(id, decision));
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

    // ------------------------------------------------------------------ CRM dashboard
    //
    // Both endpoints take the same window, so the two calls a dashboard load makes describe one
    // moment. Sending no parameters yields THIS_MONTH -- a deliberate change from the old summary,
    // which was unbounded and therefore reported all-time figures under whatever heading the UI
    // happened to put above them. Callers that really want every row ask for preset=ALL_TIME.

    @GetMapping("/summary")
    public ResponseEntity<?> getEnquirySummary(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        rejectUnknownParams(request, CRM_PARAMS);
        try {
            return ResponseEntity.ok(crmAnalyticsService.getSummary(CrmPeriod.resolve(preset, from, to)));
        } catch (InvalidDataException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error while fetching enquiry summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Failed to fetch enquiry summary: " + e.getMessage()));
        }
    }

    /** Funnel, trend and every grouped breakdown for the same window, in one response. */
    @GetMapping("/analytics")
    public ResponseEntity<?> getCrmAnalytics(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        rejectUnknownParams(request, CRM_PARAMS);
        try {
            return ResponseEntity.ok(crmAnalyticsService.getAnalytics(CrmPeriod.resolve(preset, from, to)));
        } catch (InvalidDataException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error while fetching CRM analytics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Failed to fetch CRM analytics: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ conversation log
    //
    // EnquiryConversationRecord was persisted from the first release and exposed on no endpoint:
    // the follow-up history could only be read by loading the whole enquiry, and nothing ever
    // wrote lastContactedDate. That is why "of the enquiries that went silent, how many did we
    // actually chase?" had no answer. These three endpoints, plus conversationCount and
    // lastConversationDate on the list row, are that answer.

    @GetMapping("/{id}/conversations")
    public ResponseEntity<?> getConversations(@PathVariable Long id) {
        return ResponseEntity.ok(enquiryService.getConversations(id));
    }

    @PostMapping("/{id}/conversations")
    public ResponseEntity<?> addConversation(@PathVariable Long id, @RequestBody EnquiryConversationDTO record) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enquiryService.addConversation(id, record));
    }

    @DeleteMapping("/{id}/conversations/{conversationId}")
    public ResponseEntity<?> deleteConversation(@PathVariable Long id, @PathVariable Long conversationId) {
        enquiryService.deleteConversation(id, conversationId);
        return ResponseEntity.ok(java.util.Map.of("message", "Conversation deleted"));
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
    public ResponseEntity<byte[]> exportEnquiries(@ModelAttribute EnquiryFilter filter, HttpServletRequest request) {
        // The export must answer the same question the list answered, or the spreadsheet a user
        // downloads quietly disagrees with the screen they downloaded it from.
        rejectUnknownParams(request);
        try {
            byte[] bytes = enquiryExportService.exportToExcel(filter);
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
