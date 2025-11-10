// Updated InventoryItemServiceImpl.java

package com.nextgenmanager.nextgenmanager.items.service;

import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.repository.FileAttachmentRepository;
import com.nextgenmanager.nextgenmanager.common.service.FileStorageService;
import com.nextgenmanager.nextgenmanager.items.DTO.InventoryItemDTO;
import com.nextgenmanager.nextgenmanager.items.mapper.InventoryItemMapper;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.model.ItemCode;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.*;

@Service
public class InventoryItemServiceImpl implements InventoryItemService {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ItemCodeRepository itemCodeRepository;

    @Autowired
    private InventoryItemCodeGenerator codeGenerator;

    @Autowired
    private FileAttachmentRepository fileAttachmentRepository;

    private final InventoryItemMapper inventoryItemMapper;

    @Autowired
    public InventoryItemServiceImpl(InventoryItemMapper inventoryItemMapper) {
        this.inventoryItemMapper = inventoryItemMapper;
    }

    @Autowired
    private FileStorageService fileStorageService;

    private static final Map<String, String> JOIN_FIELD_MAP = Map.of(
            "dimension", "productSpecification.dimension",
            "size", "productSpecification.size",
            "weight", "productSpecification.weight",
            "basicMaterial", "productSpecification.basicMaterial",
            "drawingNumber", "productSpecification.drawingNumber",
            "availableQuantity", "productInventorySettings.availableQuantity"
    );

    private static final Logger logger = LoggerFactory.getLogger(InventoryItem.class);

    @Override
    public InventoryItem addInventoryItem(InventoryItem inventoryItem) {
        logger.debug("Adding inventory item: {}", inventoryItem);
        try {
            // Optionally generate item code here using helper
            if(Objects.equals(inventoryItem.getItemCode(), "")) {
                String generatedCode = codeGenerator.generateItemCode(inventoryItem);
                inventoryItem.setItemCode(generatedCode);
            }
            InventoryItem savedInventoryItem = inventoryItemRepository.save(inventoryItem);
            logger.info("Item Successfully added with inventory item id: {}", savedInventoryItem.getInventoryItemId());
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

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public InventoryItem getInventoryItem(int itemId) {
        logger.debug("Fetching data for id: {}", itemId);
        try {
            InventoryItem inventoryItem = inventoryItemRepository.findByActiveId(itemId);
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


            return activeItems.map(inventoryItemMapper::toDTO);

        } catch (Exception e) {
            logger.error("Error while fetching all active inventory items: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }


    @Override
    public List<InventoryItem> getAllInventoryItemsWithDeleted() {
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

    @Override
    public InventoryItem editInventoryItem(int itemId, InventoryItem updatedItem) {
        logger.debug("Editing inventory item with id: {}", itemId);
        try {
            InventoryItem existingItem = inventoryItemRepository.findById(itemId)
                    .orElseThrow(() -> {
                        logger.warn("No inventory item found with id: {}", itemId);
                        return new IllegalArgumentException("Item does not exist");
                    });

            updatedItem.setInventoryItemId(itemId);
            updatedItem.setItemCode(existingItem.getItemCode()); // preserve original code


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
        } catch (Exception e) {
            logger.error("Error while editing inventory item with id: {}: {}", itemId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<InventoryItem> searchInventoryItems(String query, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        return inventoryItemRepository.searchActiveInventoryItems(query, pageable);
    }

    @Override
    public Page<InventoryItemDTO> filterInventoryItems(FilterRequest request) {
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
        Specification<InventoryItem> spec = InventoryItemSpecification.buildSpecification(filters,JOIN_FIELD_MAP);
        Page<InventoryItem> inventoryItems =inventoryItemRepository.findAll(spec, pageable);

        return inventoryItems.map(inventoryItemMapper::toDTO);
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
}
