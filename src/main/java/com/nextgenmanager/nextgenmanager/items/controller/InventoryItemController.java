package com.nextgenmanager.nextgenmanager.items.controller;

import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.repository.FileAttachmentRepository;
import com.nextgenmanager.nextgenmanager.common.service.FileStorageService;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemExportService;
import io.minio.GetObjectResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/inventory_item")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER'," +
        "'ROLE_INVENTORY_ADMIN','ROLE_INVENTORY_USER'," +
        "'ROLE_SALES_ADMIN','ROLE_SALES_USER'," +
        "'ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
@Tag(name = "Inventory Items", description = "Product master data — items, specifications, attachments, vendor prices")
public class InventoryItemController {

    @Autowired
    private InventoryItemService inventoryItemService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private FileAttachmentRepository fileAttachmentRepository;

    @Autowired
    private InventoryItemExportService inventoryItemExportService;

    private static final Logger logger = LoggerFactory.getLogger(InventoryItemController.class);


    @PostMapping(
            value = "/add",
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE },
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_USER')")
    public ResponseEntity<InventoryItem> addInventoryItem(
            @RequestPart("inventoryItem") InventoryItem inventoryItem,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        logger.debug("Received request to add inventory item");

        try {
            inventoryItem.setAttachments(attachments);
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
            Page<InventoryItemDTO> items = inventoryItemService.getAllInventoryItems(page, size, sortBy, sortDir,search);
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
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
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

    @PutMapping(value = "/{id}",
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE },
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<InventoryItem> editInventoryItem(
            @PathVariable int id,
            @RequestPart("inventoryItem") InventoryItem updatedItem,
            @RequestPart(value = "attachments", required = false)  List<MultipartFile> attachments) {
        logger.debug("Received request to edit inventory item with id: {}", id);
        try {
            updatedItem.setAttachments(attachments);
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
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<String> uploadFile(@PathVariable int id, @RequestPart("file") MultipartFile file) {
        try {
            fileStorageService.uploadFile(file, "inventoryItem", "inventoryItem", (long) id, "SYSTEM");
            return ResponseEntity.ok("File uploaded successfully!");
        } catch (Exception e) {
            logger.error("Error uploading file for item {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading file: " + e.getMessage());
        }
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long fileId) {
        try {
            // 1️⃣ Get metadata from DB
            FileAttachment metadata = fileAttachmentRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("File not found with ID: " + fileId));

            // 2️⃣ Get file object from MinIO
            GetObjectResponse objectResponse = fileStorageService.downloadById(fileId);

            // 3️⃣ Read bytes from the MinIO stream
            byte[] fileBytes = objectResponse.readAllBytes();

            // 4️⃣ Prepare headers and return
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(metadata.getContentType() != null
                            ? metadata.getContentType()
                            : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + metadata.getOriginalName() + "\"")
                    .body(fileBytes);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }



    @DeleteMapping("/delete-attachment/{fileId}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<String> deleteFile(@PathVariable Long fileId) {
        try {
            fileStorageService.deleteAttachment(fileId);
            return ResponseEntity.ok("File deleted successfully!");
        } catch (Exception e) {
            logger.error("Error deleting file {}: {}", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting file: " + e.getMessage());
        }
    }

    @GetMapping("/getItemCode")
    public ResponseEntity<String> generateCode(){
        return ResponseEntity.ok(inventoryItemService.generateUniqueCode());
    }


    @PostMapping("/filter")
    public Page<InventoryItemDTO> filterInventoryItems(@RequestBody FilterRequest request) {
        return inventoryItemService.filterInventoryItems(request);
    }

    @GetMapping("/export/catalog")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_SALES_ADMIN','ROLE_SALES_MANAGER')")
    public ResponseEntity<byte[]> exportProductCatalog(@RequestParam(required = false) List<Integer> ids) {
        try {
            byte[] fileBytes = inventoryItemExportService.generateProductCatalogExcel(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Product_Catalog.xlsx\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating product catalog export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export/bulk")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN')")
    public ResponseEntity<byte[]> exportBulkItemMaster(@RequestParam(required = false) List<Integer> ids) {
        try {
            byte[] fileBytes = inventoryItemExportService.generateBulkItemExportExcel(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Bulk_Item_Master.xlsx\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating bulk item export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_ENGINEERING')")
    public ResponseEntity<byte[]> exportProductMasterPdf(@RequestParam(required = false) List<Integer> ids) {
        try {
            byte[] fileBytes = inventoryItemExportService.generateProductMasterDataSheetPdf(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Product_Master_Data_Sheet.pdf\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating product master PDF export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export/vendor-prices")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_PURCHASE_ADMIN','ROLE_PURCHASE_USER')")
    public ResponseEntity<byte[]> exportVendorPrices(@RequestParam(required = false) List<Integer> ids) {
        try {
            byte[] fileBytes = inventoryItemExportService.generateVendorPriceComparisonExcel(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Vendor_Price_Comparison.xlsx\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating vendor price comparison export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export/gst-import")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_FINANCE_ADMIN','ROLE_SALES_ADMIN')")
    public ResponseEntity<byte[]> exportGstImport(@RequestParam(required = false) List<Integer> ids) {
        try {
            byte[] fileBytes = inventoryItemExportService.generateEWayBillTallyExcel(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"GST_EWay_Tally_Import.xlsx\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating GST import export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export/low-stock-indent")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_PURCHASE_ADMIN','ROLE_PURCHASE_USER')")
    public ResponseEntity<byte[]> exportLowStockIndent() {
        try {
            byte[] fileBytes = inventoryItemExportService.generateLowStockIndentExcel();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Low_Stock_Purchase_Indent.xlsx\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating low stock indent export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export/job-work-items")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_PURCHASE_ADMIN')")
    public ResponseEntity<byte[]> exportJobWorkItems(@RequestParam(required = false) List<Integer> ids) {
        try {
            byte[] fileBytes = inventoryItemExportService.generateJobWorkItemsExcel(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Job_Work_Items.xlsx\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating job work items export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
