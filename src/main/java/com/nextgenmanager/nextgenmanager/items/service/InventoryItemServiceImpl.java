// Updated InventoryItemServiceImpl.java

package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.repository.FileAttachmentRepository;
import com.nextgenmanager.nextgenmanager.common.service.FileStorageService;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.DTO.InventorySettingsBackfillDto;
import com.nextgenmanager.nextgenmanager.items.mapper.InventoryItemMapper;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemCode;
import com.nextgenmanager.nextgenmanager.items.model.ItemCodeSeries;
import com.nextgenmanager.nextgenmanager.items.model.ProductInventorySettings;
import com.nextgenmanager.nextgenmanager.items.model.ReplenishmentStrategy;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.repository.ItemCodeSeriesRepository;
import com.nextgenmanager.nextgenmanager.items.spec.InventoryItemSpecification;
import com.nextgenmanager.nextgenmanager.production.repository.ItemCodeRepository;
import okio.FileMetadata;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.*;

/**
 * Note on injection: this class used to mix a one-argument constructor (for the final mapper) with
 * {@code @Autowired} fields. Spring coped, but Mockito's {@code @InjectMocks} picks the constructor
 * and then performs NO field injection, so every other dependency arrived null and the whole test
 * class errored out. Moved to constructor injection 2026-09-05 so the tests can run at all.
 */
@Service
@RequiredArgsConstructor
public class InventoryItemServiceImpl implements InventoryItemService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ItemCodeRepository itemCodeRepository;
    private final ItemCodeSeriesRepository itemCodeSeriesRepository;
    private final InventoryItemCodeGenerator codeGenerator;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final FileStorageService fileStorageService;
    private final com.nextgenmanager.nextgenmanager.purchase.repository.PurchaseOrderRepository purchaseOrderRepository;
    private final ItemRevisionService itemRevisionService;

    private static final Map<String, String> JOIN_FIELD_MAP = Map.of(
            "dimension", "productSpecification.dimension",
            "size", "productSpecification.size",
            "weight", "productSpecification.weight",
            "basicMaterial", "productSpecification.basicMaterial",
            "drawingNumber", "productSpecification.drawingNumber"
    );

    private static final Logger logger = LoggerFactory.getLogger(InventoryItem.class);

    private boolean canAccessFinance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                               a.getAuthority().equals("ROLE_ADMIN") ||
                               a.getAuthority().equals("ROLE_SALES_ADMIN"));
    }

    @Override
    @Transactional
    public com.nextgenmanager.nextgenmanager.items.model.InventoryItem addInventoryItem(com.nextgenmanager.nextgenmanager.items.model.InventoryItem inventoryItem) {
        logger.debug("Adding inventory item: {}", inventoryItem);
        try {
            if (!canAccessFinance()) {
                inventoryItem.setProductFinanceSettings(null);
            }
            // Generate item code if not provided
            if (inventoryItem.getItemCode() == null || inventoryItem.getItemCode().isBlank()) {
                if (inventoryItem.getSeriesId() != null) {
                    // Series-based generation: use pessimistic lock to atomically increment
                    ItemCodeSeries series = itemCodeSeriesRepository.findByIdWithLock(inventoryItem.getSeriesId())
                            .orElseThrow(() -> new IllegalArgumentException("Item code series not found: " + inventoryItem.getSeriesId()));
                    String generatedCode = series.consumeNextCode();
                    itemCodeSeriesRepository.save(series);
                    inventoryItem.setItemCode(generatedCode);
                } else {
                    // Legacy year-sequence generator
                    String generatedCode = codeGenerator.generateItemCode(inventoryItem);
                    inventoryItem.setItemCode(generatedCode);
                }
            }

            // Every item needs somewhere to keep its stock figures. Nothing else in this codebase
            // ever builds this row, so an item created without one — which is every item any
            // import has ever loaded — can never hold stock, be reserved or be picked: those
            // paths all read the settings first and give up when they are null.
            if (inventoryItem.getProductInventorySettings() == null) {
                inventoryItem.setProductInventorySettings(defaultInventorySettings(inventoryItem));
            }

            InventoryItem savedInventoryItem = inventoryItemRepository.save(inventoryItem);
            logger.info("Item Successfully added with inventory item id: {}", savedInventoryItem.getInventoryItemId());

            // Every item starts life released at revision A — a part with no revision history is
            // exactly the gap that let engineering fields be edited in place with nothing to show
            // for it. Revising off this baseline is what "unlocks" it for the next real change.
            itemRevisionService.createInitialRevision(savedInventoryItem);

            long itemId = (long)savedInventoryItem.getInventoryItemId();
            // 2️⃣ Upload and save file metadata if attachments exist
            if (inventoryItem.getAttachments() != null && !inventoryItem.getAttachments().isEmpty()) {
                for (MultipartFile file : inventoryItem.getAttachments()) {
                    fileStorageService.uploadFile(
                            file,
                            "inventoryItem",
                            "inventoryItem",
                            itemId,
                            "SYSTEM"
                    );
                }
            }

            // 3️⃣ Fetch uploaded file metadata to send back to UI
            List<FileAttachment> metadataList = fileAttachmentRepository.findByReferenceTypeAndReferenceId("inventoryItem", itemId);
            savedInventoryItem.setFileAttachments(metadataList);

            return savedInventoryItem;
        } catch (Exception e) {
            logger.error("Error while adding new inventory item: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * The settings an item gets when nobody said otherwise: it holds stock, tracks nothing, and
     * has no reorder policy. Zeroes here are the honest statement that no one has set a policy,
     * which is a different thing from a policy of zero — and unlike an invented reorder level,
     * a zero cannot quietly trigger purchasing.
     */
    private ProductInventorySettings defaultInventorySettings(InventoryItem item) {
        ProductInventorySettings settings = new ProductInventorySettings();
        settings.setInventoryItem(item);
        settings.setReplenishmentStrategy(ReplenishmentStrategy.MAKE_TO_STOCK);
        settings.setBatchTracked(false);
        settings.setSerialTracked(false);
        settings.setAllowNegativeStock(false);
        settings.setAvailableQuantity(0);
        settings.setReservedQuantity(0);
        settings.setOrderedQuantity(0);
        settings.setReorderLevel(0);
        settings.setMinStock(0);
        settings.setMaxStock(0);
        settings.setLeadTime(0);
        return settings;
    }

    @Override
    @Transactional
    public InventorySettingsBackfillDto backfillInventorySettings(boolean dryRun) {
        List<InventoryItem> missing = inventoryItemRepository.findActiveWithoutInventorySettings();
        Set<Integer> everPurchased = new HashSet<>(purchaseOrderRepository.findDistinctOrderedItemIds());

        List<InventorySettingsBackfillDto.Row> rows = new ArrayList<>();
        long purchased = 0, manufactured = 0;

        for (InventoryItem item : missing) {
            boolean isPurchased = everPurchased.contains(item.getInventoryItemId());
            ProductInventorySettings settings = defaultInventorySettings(item);
            settings.setPurchased(isPurchased);
            settings.setManufactured(!isPurchased);

            if (!dryRun) {
                item.setProductInventorySettings(settings);
                inventoryItemRepository.save(item);
            }

            if (isPurchased) purchased++; else manufactured++;
            rows.add(new InventorySettingsBackfillDto.Row(
                    item.getInventoryItemId(), item.getItemCode(), item.getName(),
                    isPurchased, !isPurchased,
                    isPurchased ? "on a purchase order" : "never ordered"));
        }

        logger.warn("Inventory settings backfill{}: {} items without settings, {} purchased, {} manufactured",
                dryRun ? " (DRY RUN)" : "", missing.size(), purchased, manufactured);

        return new InventorySettingsBackfillDto(dryRun, missing.size(), purchased, manufactured, rows);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public com.nextgenmanager.nextgenmanager.items.model.InventoryItem getInventoryItem(int itemId) {
        logger.debug("Fetching data for id: {}", itemId);
        try {
            InventoryItem inventoryItem = inventoryItemRepository.findByActiveId(itemId);
            List<FileAttachment> metadataList = fileAttachmentRepository.findByReferenceTypeAndReferenceId("inventoryItem", (long) itemId);
            inventoryItem.setFileAttachments(metadataList);
            return inventoryItem;
        } catch (Exception e) {
            logger.error("Error fetching inventory item with id: {}", itemId);
            throw new RuntimeException(e);
        }
    }

//    @Override
//    public Page<InventoryItem> getAllInventoryItems(int page, int size, String sortBy, String sortDir, String query) {
//        logger.debug("Fetching all active inventory items with pagination and sorting");
//        try {
//            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
//                    ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
//            Pageable pageable = PageRequest.of(page, size, sort);
//            Page<InventoryItem> activeItems = inventoryItemRepository.findAllActiveCategory(query.toLowerCase(), pageable);
//            if (activeItems.isEmpty()) {
//                logger.warn("No active inventory items found");
//            } else {
//                logger.info("Fetched {} active inventory items", activeItems.getTotalElements());
//            }
//            return activeItems;
//        } catch (Exception e) {
//            logger.error("Error while fetching all active inventory items: {}", e.getMessage());
//            throw new RuntimeException(e);
//        }
//    }

    @Override
    public Page<InventoryItemDTO> getAllInventoryItems(int page, int size, String sortBy, String sortDir, String query) {
        logger.debug("Fetching all active inventory items with pagination and sorting");

        try {
            Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                    ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<InventoryItem> activeItems = inventoryItemRepository.findAllActiveCategory(query.toLowerCase(), pageable);


            if (activeItems.isEmpty()) {
                logger.warn("No active inventory items found");
            } else {
                logger.info("Fetched {} active inventory items", activeItems.getTotalElements());
            }


            Page<InventoryItemDTO> dtos = activeItems.map(inventoryItemMapper::toDTO);
            populateDrawingFileIds(dtos);
            return dtos;

        } catch (Exception e) {
            logger.error("Error while fetching all active inventory items: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }


    @Override
    public java.util.List<com.nextgenmanager.nextgenmanager.items.model.InventoryItem> getAllInventoryItemsWithDeleted() {
        logger.debug("Fetching all inventory items including deleted");
        try {
            List<InventoryItem> allItems = inventoryItemRepository.findAll();
            if (allItems.isEmpty()) {
                logger.warn("No inventory items found");
            } else {
                logger.info("Fetched {} inventory items including deleted", allItems.size());
            }
            return allItems;
        } catch (Exception e) {
            logger.error("Error while fetching all inventory items with deleted: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteInventoryItem(int itemId) {
        logger.debug("Deleting inventory item with id: {}", itemId);
        try {
            InventoryItem inventoryItem = inventoryItemRepository.findById(itemId)
                    .orElseThrow(() -> {
                        logger.warn("No inventory item found with id: {}", itemId);
                        return new IllegalArgumentException("Item does not exist");
                    });
            inventoryItem.setDeletedDate(new Date());
            inventoryItemRepository.save(inventoryItem);
            logger.info("Inventory item with id: {} successfully marked as deleted", itemId);
        } catch (Exception e) {
            logger.error("Error while deleting inventory item with id: {}: {}", itemId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteInventoryItemDb(int itemId) {
        logger.debug("Permanently removing inventory item with id: {}", itemId);
        try {
            inventoryItemRepository.deleteById(itemId);
            logger.info("Inventory item with id: {} permanently deleted", itemId);
        } catch (Exception e) {
            logger.error("Error while permanently deleting inventory item: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeDeletedInventoryItemDb() {
        logger.debug("Removing all soft-deleted inventory items from database");
        try {
            List<InventoryItem> deletedItems = inventoryItemRepository.findByDeletedDateIsNotNull();
            inventoryItemRepository.deleteAll(deletedItems);
            logger.info("Deleted {} soft-deleted inventory items", deletedItems.size());
        } catch (Exception e) {
            logger.error("Error while removing deleted inventory items: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Settles the item code on an edit. Codes used to be frozen at creation; they are editable as
     * of the 2026-09 renumbering, because 395 of the 758 codes on file were generated by an import
     * and have to be replaced with PEC's real scheme.
     *
     * <p>Renaming is safe: {@code itemCode} is only ever read for search and display — every
     * relationship in the schema joins on {@code inventoryItemId}, so no document loses its link
     * when a code changes.
     *
     * <p>A blank or absent code means "leave it alone", so existing callers that never sent one
     * keep working unchanged.
     */
    private void applyItemCodeChange(InventoryItem existing, InventoryItem updated) {
        String wanted = updated.getItemCode() == null ? null : updated.getItemCode().trim();
        if (wanted == null || wanted.isEmpty() || wanted.equalsIgnoreCase(existing.getItemCode())) {
            updated.setItemCode(existing.getItemCode());
            return;
        }
        // Case-insensitive, because the lookup helpers are: allowing STR-100 beside str-100 would
        // give two items one identity to every search in the application. Soft-deleted items count
        // too — they still hold their code under the database's unpredicated UNIQUE(itemcode).
        if (inventoryItemRepository.itemCodeTakenByAnother(wanted, existing.getInventoryItemId())) {
            String owner = inventoryItemRepository
                    .findByItemCodeIgnoreCaseAndDeletedDateIsNull(wanted)
                    .map(o -> "'" + o.getName() + "'")
                    .orElse("a deleted item");
            throw new IllegalArgumentException(
                    "Item code '" + wanted + "' is already used by " + owner + ".");
        }
        logger.info("Item {} renamed: {} -> {}", existing.getInventoryItemId(),
                existing.getItemCode(), wanted);
        updated.setItemCode(wanted);
    }

    @Override
    public com.nextgenmanager.nextgenmanager.items.model.InventoryItem editInventoryItem(int itemId, com.nextgenmanager.nextgenmanager.items.model.InventoryItem updatedItem) {
        logger.debug("Editing inventory item with id: {}", itemId);
        try {
            InventoryItem existingItem = inventoryItemRepository.findById(itemId)
                    .orElseThrow(() -> {
                        logger.warn("No inventory item found with id: {}", itemId);
                        return new IllegalArgumentException("Item does not exist");
                    });

            updatedItem.setInventoryItemId(itemId);
            applyItemCodeChange(existingItem, updatedItem);
            itemRevisionService.assertEngineeringFieldsUnchanged(existingItem, updatedItem);
            // The revision relationship isn't part of the incoming payload — carry it forward so
            // save() doesn't null it out.
            updatedItem.setCurrentRevision(existingItem.getCurrentRevision());

            if (!canAccessFinance()) {
                com.nextgenmanager.nextgenmanager.items.model.ProductFinanceSettings existingFinance = existingItem.getProductFinanceSettings();
                if (existingFinance != null) {
                    existingFinance.setInventoryItem(updatedItem);
                    updatedItem.setProductFinanceSettings(existingFinance);
                } else {
                    updatedItem.setProductFinanceSettings(null);
                }
            } else {
                if (updatedItem.getProductFinanceSettings() != null) {
                    updatedItem.getProductFinanceSettings().setInventoryItem(updatedItem);
                }
            }

            if (updatedItem.getProductSpecification() != null) {
                updatedItem.getProductSpecification().setInventoryItem(updatedItem);
            }
            if (updatedItem.getProductInventorySettings() != null) {
                updatedItem.getProductInventorySettings().setInventoryItem(updatedItem);
            } else if (existingItem.getProductInventorySettings() != null) {
                // The association is orphanRemoval, so an update that simply does not mention the
                // settings would delete them — taking the item's stock figures with it. A caller
                // that says nothing about them means to leave them alone.
                ProductInventorySettings kept = existingItem.getProductInventorySettings();
                kept.setInventoryItem(updatedItem);
                updatedItem.setProductInventorySettings(kept);
            }

            InventoryItem newItem = inventoryItemRepository.save(updatedItem);


            List<FileAttachment> updatedItemFileAttachments = updatedItem.getFileAttachments();
            List<FileAttachment> existingFileAttachments =
                    fileAttachmentRepository.findByReferenceTypeAndReferenceId("inventoryItem", (long) itemId);


            for (FileAttachment oldFile : existingFileAttachments) {
                boolean stillExists = updatedItemFileAttachments.stream()
                        .anyMatch(newFile -> newFile.getFileName().equals(oldFile.getFileName()));
                if (!stillExists) {
                    logger.info("Deleting file removed from UI: {}", oldFile.getFileName());
                    fileStorageService.deleteAttachment(oldFile.getId());
                }
            }

            if (updatedItem.getAttachments() != null && !updatedItem.getAttachments().isEmpty()) {
                for (MultipartFile file : updatedItem.getAttachments()) {
                    if (!fileStorageService.existsInStorage(existingFileAttachments, file)) {
                        fileStorageService.uploadFile(
                                file,
                                "inventoryItem",
                                "inventoryItem",
                                (long) itemId,
                                "SYSTEM"
                        );
                        logger.info("Uploaded new file: {}", file.getOriginalFilename());
                    } else {
                        logger.debug("Skipping existing file: {}", file.getOriginalFilename());
                    }
                }
            }
            newItem.setFileAttachments(fileAttachmentRepository.findByReferenceTypeAndReferenceId("inventoryItem",(long) itemId));
            logger.info("Inventory item with id: {} successfully updated", itemId);
            return newItem;
        } catch (IllegalArgumentException e) {
            // A rejected item code, or an unknown id, is the caller's mistake — not a server fault.
            // Wrapping it in RuntimeException turned "that code is already used by X" into a bare
            // 500, hiding the one message that tells the user how to fix it.
            throw e;
        } catch (Exception e) {
            logger.error("Error while editing inventory item with id: {}: {}", itemId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<com.nextgenmanager.nextgenmanager.items.model.InventoryItem> searchInventoryItems(String query, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        return inventoryItemRepository.searchActiveInventoryItems(query, pageable);
    }

    @Override
    public Page<InventoryItemDTO> filterInventoryItems(com.nextgenmanager.nextgenmanager.common.dto.FilterRequest request) {
        Sort.Direction direction = Sort.Direction.fromString(request.getSortDir()); // safer
        String sortBy = request.getSortBy();
        if (JOIN_FIELD_MAP.containsKey(sortBy)) {
            sortBy = JOIN_FIELD_MAP.get(sortBy);
        }
        Sort sort = Sort.by(direction, sortBy);

        List<FilterCriteria> filters = request.getFilters();
        FilterCriteria filterDeleteDateIsNull = new FilterCriteria("deletedDate", "=", null);
        filters.add(filterDeleteDateIsNull);
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        Specification<com.nextgenmanager.nextgenmanager.items.model.InventoryItem> spec = InventoryItemSpecification.buildSpecification(filters,JOIN_FIELD_MAP);
        Page<com.nextgenmanager.nextgenmanager.items.model.InventoryItem> inventoryItems =inventoryItemRepository.findAll(spec, pageable);

        Page<InventoryItemDTO> dtos = inventoryItems.map(inventoryItemMapper::toDTO);
        populateDrawingFileIds(dtos);
        return dtos;
    }

    private void populateDrawingFileIds(Page<InventoryItemDTO> dtos) {
        if (dtos.isEmpty()) return;

        List<Long> ids = dtos.stream()
                .map(d -> (long) d.getInventoryItemId())
                .toList();

        List<FileAttachment> allAttachments = fileAttachmentRepository.findByReferenceTypeAndReferenceIdIn("inventoryItem", ids);

        Map<Long, List<FileAttachment>> attachmentMap = new HashMap<>();
        for (FileAttachment fa : allAttachments) {
            attachmentMap.computeIfAbsent(fa.getReferenceId(), k -> new ArrayList<>()).add(fa);
        }

        for (InventoryItemDTO dto : dtos) {
            List<FileAttachment> attachments = attachmentMap.get((long) dto.getInventoryItemId());
            if (attachments != null && !attachments.isEmpty()) {
                // Heuristic: Find attachment matching drawingNumber, or first one
                FileAttachment drawing = attachments.stream()
                        .filter(a -> dto.getDrawingNumber() != null && !dto.getDrawingNumber().isBlank() &&
                                a.getOriginalName().toLowerCase().contains(dto.getDrawingNumber().toLowerCase()))
                        .findFirst()
                        .orElse(attachments.get(0));
                dto.setDrawingFileId(drawing.getId());
            }
        }
    }

    @Override
    public String generateUniqueCode() {
        int year = Year.now().getValue();
        Integer latestSeq = itemCodeRepository.findMaxSequenceForYear(year);
        int newSeq = (latestSeq != null ? latestSeq + 1 : 1);

        String code = String.format("PEC%d%04d", year, newSeq);  // e.g. PEC20250001

        // Save to database
        ItemCode newEntry = new ItemCode();
        newEntry.setYear(year);
        newEntry.setSequenceNumber(newSeq);
        newEntry.setCode(code);
        itemCodeRepository.save(newEntry);

        return code;
    }

    @Override
    public boolean checkItemCodeExists(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) return false;
        return inventoryItemRepository.existsByItemCodeAndDeletedDateIsNull(itemCode.trim());
    }
}
