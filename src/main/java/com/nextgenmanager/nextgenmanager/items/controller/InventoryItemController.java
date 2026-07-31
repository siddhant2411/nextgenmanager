package com.nextgenmanager.nextgenmanager.items.controller;

import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.repository.FileAttachmentRepository;
import com.nextgenmanager.nextgenmanager.common.service.FileStorageService;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.DTO.PriceListExportRequest;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemExportService;
import com.nextgenmanager.nextgenmanager.items.service.PriceListExportService;
import io.minio.GetObjectResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/inventory_item")
@RequiresAllModulesAccess
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

    @Autowired
    private PriceListExportService priceListExportService;

    private static final Logger logger = LoggerFactory.getLogger(InventoryItemController.class);

    private boolean canViewFinance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_ADMIN") ||
                               a.getAuthority().equals("ROLE_SALES_ADMIN"));
    }


    @PostMapping(
            value = "/add",
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE },
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @RequiresInventoryAccess
    public ResponseEntity<InventoryItem> addInventoryItem(
            @Valid @RequestPart("inventoryItem") InventoryItem inventoryItem,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) throws Exception {
        logger.debug("Received request to add inventory item");

        inventoryItem.setAttachments(attachments);
        InventoryItem savedItem = inventoryItemService.addInventoryItem(inventoryItem);
        if (!canViewFinance() && savedItem != null) {
            savedItem.setProductFinanceSettings(null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(savedItem);
    }

    @GetMapping("/{id}")
    @RequiresAllModulesAccess
    public ResponseEntity<InventoryItem> getInventoryItem(@PathVariable String id) {
        logger.debug("Received request to fetch inventory item with id: {}", Integer.parseInt(id));
        try {
            InventoryItem inventoryItem = inventoryItemService.getInventoryItem(Integer.parseInt(id));
            if (!canViewFinance() && inventoryItem != null) {
                inventoryItem.setProductFinanceSettings(null);
            }
            return ResponseEntity.ok(inventoryItem);
        } catch (Exception e) {
            logger.error("Error fetching inventory item: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_EXTENDED).build();
        }
    }

    @GetMapping("/all")
    @RequiresAllModulesAccess
    public ResponseEntity<?> getAllInventoryItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "inventoryItemId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "") String search){
        logger.debug("Received request to fetch all active inventory items with pagination and sorting");
        try {
            Page<InventoryItemDTO> items = inventoryItemService.getAllInventoryItems(page, size, sortBy, sortDir,search);
            if (!canViewFinance() && items != null) {
                items.forEach(dto -> dto.setStandardCost(null));
            }
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error fetching all inventory items: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @GetMapping("/all-with-deleted")
    @RequiresInventoryAdminAccess
    public ResponseEntity<List<InventoryItem>> getAllInventoryItemsWithDeleted() {
        logger.debug("Received request to fetch all inventory items including deleted");
        try {
            List<InventoryItem> items = inventoryItemService.getAllInventoryItemsWithDeleted();
            if (!canViewFinance() && items != null) {
                items.forEach(item -> item.setProductFinanceSettings(null));
            }
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Error fetching inventory items with deleted: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_EXTENDED).build();
        }
    }

    @DeleteMapping("/{id}")
    @RequiresInventoryAdminAccess
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
            @Valid @RequestPart("inventoryItem") InventoryItem updatedItem,
            @RequestPart(value = "attachments", required = false)  List<MultipartFile> attachments) throws Exception {
        logger.debug("Received request to edit inventory item with id: {}", id);
        updatedItem.setAttachments(attachments);
        InventoryItem savedItem = inventoryItemService.editInventoryItem(id, updatedItem);
        if (!canViewFinance() && savedItem != null) {
            savedItem.setProductFinanceSettings(null);
        }
        return ResponseEntity.ok(savedItem);
    }

    @GetMapping("/search")
    @RequiresAllModulesAccess
    public Page<InventoryItem> searchInventoryItems(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5"
            ) int size) {
        Page<InventoryItem> result = inventoryItemService.searchInventoryItems(query, page, size);
        if (!canViewFinance() && result != null) {
            result.forEach(item -> item.setProductFinanceSettings(null));
        }
        return result;
    }

    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresInventoryAdminAccess
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
    @RequiresAllModulesAccess
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
    @RequiresInventoryAdminAccess
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
    @RequiresInventoryAccess
    public ResponseEntity<String> generateCode(){
        return ResponseEntity.ok(inventoryItemService.generateUniqueCode());
    }

    @GetMapping("/check-code")
    @RequiresAllModulesAccess
    public ResponseEntity<?> checkItemCode(@RequestParam String itemCode) {
        boolean exists = inventoryItemService.checkItemCodeExists(itemCode);
        return ResponseEntity.ok(Map.of("exists", exists));
    }


    @PostMapping("/filter")
    @RequiresAllModulesAccess
    public Page<InventoryItemDTO> filterInventoryItems(@RequestBody FilterRequest request) {
        Page<InventoryItemDTO> result = inventoryItemService.filterInventoryItems(request);
        if (!canViewFinance() && result != null) {
            result.forEach(dto -> dto.setStandardCost(null));
        }
        return result;
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

    /**
     * Exports a sales price list for the selected (or filtered) items as PDF or Excel.
     *
     * <p>{@code audience=CUSTOMER} discloses list price and GST only and is safe to send out.
     * {@code audience=INTERNAL} additionally discloses cost, margin, floor price and maximum
     * discount, and is therefore restricted to roles that may already see finance data.
     * Manufacturing cost never appears on the customer variant.
     */
    @PostMapping("/export/price-list")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_INVENTORY_ADMIN','ROLE_SALES_ADMIN','ROLE_SALES_MANAGER')")
    public ResponseEntity<byte[]> exportPriceList(@RequestBody PriceListExportRequest request) {
        if (request.isInternal() && !canViewFinance()) {
            logger.warn("Rejected INTERNAL price list export: caller lacks finance visibility");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            boolean excel = request.isExcel();
            byte[] fileBytes = excel
                    ? priceListExportService.generateExcel(request)
                    : priceListExportService.generatePdf(request);

            String filename = "Price_List_" + LocalDate.now() + (excel ? ".xlsx" : ".pdf");
            String contentType = excel
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    : MediaType.APPLICATION_PDF_VALUE;

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating price list export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export/bulk")
    @RequiresInventoryAdminAccess
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
