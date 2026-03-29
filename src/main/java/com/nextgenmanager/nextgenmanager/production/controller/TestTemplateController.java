package com.nextgenmanager.nextgenmanager.production.controller;

import com.nextgenmanager.nextgenmanager.production.dto.TestTemplateDTO;
import com.nextgenmanager.nextgenmanager.production.service.TestTemplateService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manufacturing/test-template")
@CrossOrigin
public class TestTemplateController {

    private static final Logger logger = LoggerFactory.getLogger(TestTemplateController.class);

    @Autowired
    private TestTemplateService testTemplateService;

    /**
     * Get active test templates for an inventory item.
     */
    @GetMapping("/item/{itemId}")
    public ResponseEntity<?> getTemplatesForItem(@PathVariable int itemId) {
        try {
            List<TestTemplateDTO> templates = testTemplateService.getTemplatesForItem(itemId);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            logger.error("Error fetching test templates for item {}", itemId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all test templates (including inactive) for an inventory item.
     */
    @GetMapping("/item/{itemId}/all")
    public ResponseEntity<?> getAllTemplatesForItem(@PathVariable int itemId) {
        try {
            List<TestTemplateDTO> templates = testTemplateService.getAllTemplatesForItem(itemId);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            logger.error("Error fetching all test templates for item {}", itemId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Create a new test template.
     */
    @PostMapping
    public ResponseEntity<?> createTestTemplate(@RequestBody TestTemplateDTO dto) {
        try {
            TestTemplateDTO created = testTemplateService.createTestTemplate(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating test template", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update an existing test template.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTestTemplate(@PathVariable Long id, @RequestBody TestTemplateDTO dto) {
        try {
            TestTemplateDTO updated = testTemplateService.updateTestTemplate(id, dto);
            return ResponseEntity.ok(updated);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error updating test template {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Soft-delete a test template.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTestTemplate(@PathVariable Long id) {
        try {
            testTemplateService.softDeleteTestTemplate(id);
            return ResponseEntity.ok(Map.of("message", "TestTemplate deleted successfully"));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting test template {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
