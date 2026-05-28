package com.nextgenmanager.nextgenmanager.marketing.quotation.controller;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.marketing.quotation.dto.QuotationDisplayDTO;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.service.QuotationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/quotation")
@RequiresSalesAccess
@Validated
public class QuotationController {

    @Autowired
    private QuotationService quotationService;

    Logger logger = LoggerFactory.getLogger(QuotationController.class);
    @GetMapping("/{id}")
    public ResponseEntity<?> getQuotationById(@PathVariable Long id) {
        try {
            Quotation quotation = quotationService.getQuotationById(id);
            return ResponseEntity.ok(quotation);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<Page<QuotationDisplayDTO>> getAllQuotations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "enqDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String qtnNo,
            @RequestParam(required = false) LocalDate qtnDate,
            @RequestParam(required = false) LocalDate enqDate,
            @RequestParam(required = false) String enqNo,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) BigDecimal netAmount,
            @RequestParam(required = false) BigDecimal totalAmount
    ) {
        logger.info("Fetching all quotations with filters applied");
        try {
            // Validate sort direction
            if (!sortDir.equalsIgnoreCase("asc") && !sortDir.equalsIgnoreCase("desc")) {
                throw new IllegalArgumentException("Invalid sort direction. Use 'asc' or 'desc'.");
            }

            Page<QuotationDisplayDTO> quotations = quotationService.getQuotationDisplayList(
                    page, size, sortBy, sortDir, qtnNo, qtnDate, enqDate, enqNo, companyName, netAmount, totalAmount);

            return ResponseEntity.ok(quotations);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request parameter: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Unexpected error while fetching quotations: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @PostMapping
    public ResponseEntity<Quotation> createQuotation(@RequestBody Quotation quotation) {
        try {
            Quotation createdQuotation = quotationService.createQuotation(quotation);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdQuotation);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Quotation> updateQuotation(@PathVariable Long id, @RequestBody Quotation updatedQuotation) {
        try {
            Quotation updated = quotationService.updateQuotation(updatedQuotation, id);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteQuotation(@PathVariable Long id) {
        try {
            quotationService.deleteQuotation(id);
            return ResponseEntity.ok("Quotation deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Quotation not found.");
        }
    }

    @GetMapping("/pdf/{id}")
    public ResponseEntity<byte[]> downloadQuotationPdf(@PathVariable Long id) {
        return quotationService.downloadQuotationPdf(id);
    }
    
    @PostMapping("/{id}/revise")
    public ResponseEntity<?> reviseQuotation(@PathVariable Long id) {
        try {
            Quotation revised = quotationService.reviseQuotation(id);
            return ResponseEntity.status(HttpStatus.CREATED).body(revised);
        } catch (Exception e) {
            logger.error("Error revising quotation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
    
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateQuotationStatus(@PathVariable Long id, @RequestBody java.util.Map<String, String> payload) {
        try {
            String status = payload.get("status");
            if (status == null) {
                return ResponseEntity.badRequest().body("Status is required");
            }
            Quotation updated = quotationService.updateQuotationStatus(id, status);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("Error updating quotation status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}

