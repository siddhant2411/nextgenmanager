package com.nextgenmanager.nextgenmanager.items.controller;

import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItemAttachment;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemAttachmentService;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory_item")

public class InventoryItemController {

    @Autowired
    private InventoryItemService inventoryItemService;

    @Autowired
    private InventoryItemAttachmentService attachmentService;

    private static final Logger logger = LoggerFactory.getLogger(InventoryItemController.class);

    private static final String UPLOAD_DIR = "files/";


    @PostMapping("/add")
    public ResponseEntity<InventoryItem> addInventoryItem(@RequestBody InventoryItem inventoryItem) {
        logger.debug("Received request to add inventory item");
        try {
            InventoryItem savedItem = inventoryItemService.addInventoryItem(inventoryItem);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedItem);
        } catch (Exception e) {
            logger.error("Error adding inventory item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryItem> getInventoryItem(@PathVariable String id) {
        logger.debug("Received request to fetch inventory item with id: {}", Integer.parseInt(id));
        try {
            InventoryItem inventoryItem = inventoryItemService.getInventoryItem(Integer.parseInt(id));
            return ResponseEntity.ok(inventoryItem);
        } catch (Exception e) {
            logger.error("Error fetching inventory item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_EXTENDED).build();
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllInventoryItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "inventoryItemId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "") String search){
        logger.debug("Received request to fetch all active inventory items with pagination and sorting");
        try {
            Page<InventoryItem> items = inventoryItemService.getAllInventoryItems(page, size, sortBy, sortDir,search);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error fetching all inventory items: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/all-with-deleted")
    public ResponseEntity<List<InventoryItem>> getAllInventoryItemsWithDeleted() {
        logger.debug("Received request to fetch all inventory items including deleted");
        try {
            List<InventoryItem> items = inventoryItemService.getAllInventoryItemsWithDeleted();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error fetching inventory items with deleted: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_EXTENDED).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInventoryItem(@PathVariable int id) {
        logger.debug("Received request to delete inventory item with id: {}", id);
        try {
            inventoryItemService.deleteInventoryItem(id);
            return ResponseEntity.status(HttpStatus.OK).build();
        } catch (IllegalArgumentException e) {
            logger.warn("Item not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error deleting inventory item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryItem> editInventoryItem(
            @PathVariable int id,
            @RequestBody InventoryItem updatedItem) {
        logger.debug("Received request to edit inventory item with id: {}", id);
        try {
            InventoryItem savedItem = inventoryItemService.editInventoryItem(id, updatedItem);
            return ResponseEntity.ok(savedItem);
        } catch (IllegalArgumentException e) {
            logger.warn("Item not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Error editing inventory item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/search")
    public Page<InventoryItem> searchInventoryItems(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5"
            ) int size) {
        return inventoryItemService.searchInventoryItems(query, page, size);
    }

    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@PathVariable int id, @RequestParam("file") MultipartFile file) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Save file details in the database
            InventoryItemAttachment attachment = new InventoryItemAttachment();
            attachment.setFileName(fileName);
            attachment.setFilePath(filePath.toString());
            attachment.setFileType(file.getContentType());
            attachmentService.saveAttachment(id, attachment);

            return ResponseEntity.ok("File uploaded successfully!");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading file: " + e.getMessage());
        }
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<UrlResource> downloadFile(@PathVariable Long fileId) {
        try {
            Optional<InventoryItemAttachment> attachmentOpt = attachmentService.getAttachmentById(fileId);
            if (attachmentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            InventoryItemAttachment attachment = attachmentOpt.get();
            Path filePath = Paths.get(attachment.getFilePath()).normalize();
            UrlResource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    @DeleteMapping("/delete/{fileId}")
    public ResponseEntity<String> deleteFile(@PathVariable Long fileId) {
        try {
            Optional<InventoryItemAttachment> attachmentOpt = attachmentService.getAttachmentById(fileId);
            if (attachmentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found!");
            }

            InventoryItemAttachment attachment = attachmentOpt.get();
            Path filePath = Paths.get(attachment.getFilePath());

            // Delete file from the storage
            Files.deleteIfExists(filePath);

            // Remove entry from the database
            attachmentService.deleteAttachment(fileId);


            return ResponseEntity.ok("File deleted successfully!");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting file: " + e.getMessage());
        }
    }

    @GetMapping("/getItemCode")
    public ResponseEntity<String> generateCode(){
        return ResponseEntity.ok(inventoryItemService.generateUniqueCode());
    }


}
