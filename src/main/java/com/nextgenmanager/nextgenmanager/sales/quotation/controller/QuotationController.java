package com.nextgenmanager.nextgenmanager.sales.quotation.controller;

import com.nextgenmanager.nextgenmanager.sales.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.sales.quotation.service.QuotationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quotations")
public class QuotationController {

    @Autowired
    private QuotationService quotationService;

    @GetMapping("/{id}")
    public ResponseEntity<Quotation> getQuotationById(@PathVariable int id) {
        try {
            Quotation quotation = quotationService.getQuotationById(id);
            return ResponseEntity.ok(quotation);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @GetMapping
    public ResponseEntity<List<Quotation>> getAllQuotations() {
        List<Quotation> quotations = quotationService.getQuotationList();
        return ResponseEntity.ok(quotations);
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
}
