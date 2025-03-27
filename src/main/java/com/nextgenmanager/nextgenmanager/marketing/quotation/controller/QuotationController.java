package com.nextgenmanager.nextgenmanager.marketing.quotation.controller;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.marketing.quotation.dto.QuotationDisplayDTO;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.service.QuotationService;
import com.nextgenmanager.nextgenmanager.marketing.quotation.service.QuotationServiceImp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/quotation")
@CrossOrigin(origins = "*")
public class QuotationController {

    @Autowired
    private QuotationService quotationService;

    Logger logger = LoggerFactory.getLogger(QuotationController.class);
    @GetMapping("/{id}")
    public ResponseEntity<?> getQuotationById(@PathVariable String id) {
        try {
            Quotation quotation = quotationService.getQuotationById(Integer.parseInt(id));
            return ResponseEntity.ok(quotation);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<Page<QuotationDisplayDTO>> getAllQuotations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
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
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Quotation> updateQuotation(@PathVariable int id, @RequestBody Quotation updatedQuotation) {
        try {
            Quotation updated = quotationService.updateQuotation(updatedQuotation, id);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteQuotation(@PathVariable int id) {
        try {
            quotationService.deleteQuotation(id);
            return ResponseEntity.ok("Quotation deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Quotation not found.");
        }
    }

    @GetMapping("/pdf/{id}")
    public ResponseEntity<byte[]> downloadSubscriptionReceipt(@PathVariable String id) {
        return quotationService.downloadQuotationPdf(Integer.parseInt(id));
    }
}
