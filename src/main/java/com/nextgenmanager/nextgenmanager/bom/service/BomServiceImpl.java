package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BOMTemplateMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomAttachment;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.repository.BomAttachmentRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.BomPositionRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderProductionTemplateRepository;
import com.nextgenmanager.nextgenmanager.production.service.WorkOrderProductionTemplateService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class BomServiceImpl implements BomService {
    private static final Logger logger = LoggerFactory.getLogger(BomServiceImpl.class);

    @Autowired
    private BomRepository bomRepository;

    @Autowired
    private BomPositionRepository bomPositionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private InventoryItemService inventoryItemService;

    @Autowired
    private WorkOrderProductionTemplateRepository workOrderProductionTemplateRepository;

    @Autowired
    private BomAttachmentRepository bomAttachmentRepository;

    private static final String UPLOAD_DIR = "files/bom/";

    @Override
    public Bom addBom(Bom bom) {
        logger.info("Starting to add new BOM for parent inventory item ID: {}",
                bom.getParentInventoryItem().getInventoryItemId());

        try {
            // Validate parent inventory item exists
            if (!inventoryItemRepository.existsById(bom.getParentInventoryItem().getInventoryItemId())) {
                logger.error("Parent inventory item not found with ID: {}",
                        bom.getParentInventoryItem().getInventoryItemId());
                throw new ResourceNotFoundException("Parent inventory item not found");
            }

            // Validate BOM positions
            if (bom.getChildInventoryItems() != null) {
                for (BomPosition position : bom.getChildInventoryItems()) {
                    if (position.getChildInventoryItem() == null ||
                            !inventoryItemRepository.existsById(position.getChildInventoryItem().getInventoryItemId())) {
                        logger.error("Invalid child inventory item in BOM position");
                        throw new InvalidDataException("Invalid child inventory item in BOM position");
                    }
                    if (position.getQuantity() <= 0) {
                        logger.error("Invalid quantity in BOM position: {}", position.getQuantity());
                        throw new InvalidDataException("Quantity must be greater than 0");
                    }
                }

                // Save BOM positions first
                logger.debug("Saving {} BOM positions", bom.getChildInventoryItems().size());
                bomPositionRepository.saveAll(bom.getChildInventoryItems());
            }

            // Save the BOM
            Bom savedBom = bomRepository.save(bom);
            logger.info("Successfully created BOM with ID: {}", savedBom.getId());
            return bomRepository.findById(savedBom.getId())
                    .orElseThrow(() -> new RuntimeException("Failed to create BOM"));

        } catch (Exception e) {
            logger.error("Error while creating BOM: {}", e.getMessage());
            throw new BomServiceException("Failed to create BOM", e);
        }
    }

    private Optional<Integer> extractVersion(BomAttachment attachment) {
        String fileName = attachment.getFileName();
        Pattern pattern = Pattern.compile("(?i)v(\\d+)"); // Case-insensitive match for 'v' followed by digits
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.find()) {
            try {
                return Optional.of(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException e) {
                // Log the exception if needed
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String extractBaseFileName(String fileName) {
        return fileName.replaceAll("(?i)_v\\d+(\\.\\w+)$", "$1"); // Extract base filename
    }

    private List<BomAttachment> getLatestAttachments(Bom bom) {
        List<BomAttachment> attachments = bom.getBomAttachmentList();
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }

        return attachments.stream()
                .filter(att -> att != null && extractVersion(att).isPresent()) // Ensure att is not null and version is present
                .collect(Collectors.groupingBy(att -> extractBaseFileName(att.getFileName())))
                .values().stream()
                .map(list -> list.stream()
                        .filter(Objects::nonNull)
                        .max(Comparator.comparing(att -> extractVersion(att).orElse(0))))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }


    @Override
    public Bom getBom(int id) {
        logger.info("Fetching BOM with ID: {}", id);

        return bomRepository.findById(id)
                .filter(bom -> bom.getDeletedDate() == null)
                .map(originalBom -> {
                    // Create a deep copy of the original BOM with all fields
                    Bom bomCopy = new Bom();
                    bomCopy.setId(originalBom.getId());
                    bomCopy.setBomName(originalBom.getBomName());
                    bomCopy.setParentInventoryItem(originalBom.getParentInventoryItem());
                    bomCopy.setChildInventoryItems(originalBom.getChildInventoryItems());
                    bomCopy.setCreationDate(originalBom.getCreationDate());
                    bomCopy.setUpdatedDate(originalBom.getUpdatedDate());
                    bomCopy.setDeletedDate(originalBom.getDeletedDate());

                    // **Fetch and set only the latest version of each file**
                    bomCopy.setBomAttachmentList(getLatestAttachments(originalBom));

                    return bomCopy;
                })
                .orElseThrow(() -> {
                    logger.error("BOM not found or deleted with ID: {}", id);
                    return new ResourceNotFoundException("BOM not found with ID: " + id);
                });
    }





    @Override
    public Bom deleteBom(int id) {
        logger.info("Starting deletion of BOM with ID: {}", id);

        try {
            Bom existingBom = getBom(id); // This will throw if not found

            // Soft delete - update the deletedDate
            existingBom.setDeletedDate(new Date());

            // Save the updated BOM
            Bom deletedBom = bomRepository.save(existingBom);
            logger.info("Successfully soft-deleted BOM with ID: {}", id);

            return deletedBom;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error while deleting BOM with ID {}: {}", id, e.getMessage());
            throw new BomServiceException("Failed to delete BOM", e);
        }
    }

    @Override
    public Bom editBom(Bom bom) {
        logger.info("Starting to edit BOM with ID: {}", bom.getId());

        try {
            // Check if BOM exists
            if (!bomRepository.existsById(bom.getId())) {
                logger.error("BOM not found with ID: {}", bom.getId());
                throw new ResourceNotFoundException("BOM not found with ID: " + bom.getId());
            }

            // Validate parent inventory item
            if (!inventoryItemRepository.existsById(bom.getParentInventoryItem().getInventoryItemId())) {
                logger.error("Parent inventory item not found with ID: {}",
                        bom.getParentInventoryItem().getInventoryItemId());
                throw new ResourceNotFoundException("Parent inventory item not found");
            }

            // Validate and update BOM positions
            if (bom.getChildInventoryItems() != null) {
                for (BomPosition position : bom.getChildInventoryItems()) {
                    if (position.getChildInventoryItem() == null ||
                            !inventoryItemRepository.existsById(position.getChildInventoryItem().getInventoryItemId())) {
                        logger.error("Invalid child inventory item in BOM position");
                        throw new InvalidDataException("Invalid child inventory item in BOM position");
                    }
                    if (position.getQuantity() <= 0) {
                        logger.error("Invalid quantity in BOM position: {}", position.getQuantity());
                        throw new InvalidDataException("Quantity must be greater than 0");
                    }
                }

                // Update BOM positions
                bom.setBomAttachmentList(getBom(bom.getId()).getBomAttachmentList());
                bomPositionRepository.saveAll(bom.getChildInventoryItems());
            }

            // Save the updated BOM
            Bom updatedBom = bomRepository.save(bom);
            logger.info("Successfully updated BOM with ID: {}", updatedBom.getId());
            return updatedBom;

        } catch (Exception e) {
            logger.error("Error while updating BOM with ID {}: {}", bom.getId(), e.getMessage());
            throw new BomServiceException("Failed to update BOM", e);
        }
    }

    @Override
    public Page<BomDTO> getAllBom(int page, int size, String sortBy, String sortDir, String query) {
        logger.debug("Fetching all active BOMs with page: {}, size: {}, sortBy: {}, sortDir: {}, query: {}", page, size, sortBy, sortDir, query);

        try {
            // Validate sort direction and apply default if invalid
            if(sortBy.equals("itemCode")||sortBy.equals("name")){
                sortBy = "i."+sortBy;
            }
            Sort.Direction direction = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            Sort sort = Sort.by(direction, sortBy);


            // Create a pageable object with sort information
            Pageable pageable = PageRequest.of(page, size, sort);

            // Fetch the BOMs using the repository
            Page<BomDTO> boms = bomRepository.findAllActiveBom(query.toLowerCase(), pageable);

            // Logging based on results
            if (boms.isEmpty()) {
                logger.warn("No active BOM found for query: {}", query);
            } else {
                logger.info("Fetched {} active BOM(s)", boms.getTotalElements());
            }

            return boms;

        } catch (Exception e) {
            logger.error("Error while fetching all BOMs: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching BOM data", e);
        }
    }

    @Override
    public void saveAttachment(int id, MultipartFile file) throws IOException {
        logger.info("Saving attachment for ID: {}", id);

        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            logger.info("Created upload directory: {}", uploadPath);
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            logger.error("File name is empty for ID: {}", id);
            throw new IOException("File name is empty");
        }

        // Extract filename and extension
        int dotIndex = originalFileName.lastIndexOf(".");
        String baseName = (dotIndex == -1) ? originalFileName : originalFileName.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : originalFileName.substring(dotIndex);

        int version = 1;
        String fileName = baseName + "_v" + version + extension;
        Path filePath = uploadPath.resolve(fileName);

        // Increment version if file exists
        while (Files.exists(filePath)) {


            version++;
            fileName = baseName + "_v" + version + extension;
            filePath = uploadPath.resolve(fileName);
            logger.info("File already exists, incrementing version: {}", fileName);
        }

        // Save the file
        Files.copy(file.getInputStream(), filePath);
        logger.info("File saved successfully: {}", fileName);

        // Save file details in the database
        BomAttachment attachment = new BomAttachment();
        attachment.setFileName(fileName);
        attachment.setFilePath(filePath.toString());
        attachment.setFileType(file.getContentType());
        attachment.setBom(getBom(id));
        bomAttachmentRepository.save(attachment);

        logger.info("Attachment details saved to database for file: {}", fileName);
    }


    private Path getFilePath(Long fileId) {
        Optional<BomAttachment> attachmentOpt = bomAttachmentRepository.findById(fileId);

        if (attachmentOpt.isEmpty()) {
            logger.error("File not found for ID: {}", fileId);
            throw new ResourceNotFoundException("File not found for ID: " + fileId);
        }

        BomAttachment attachment = attachmentOpt.get();
        return Paths.get(attachment.getFilePath()).toAbsolutePath().normalize();
    }

    @Override
    public UrlResource getAttachmentById(Long fileId) throws MalformedURLException {
        Path filePath = getFilePath(fileId);
        UrlResource resource = new UrlResource(filePath.toUri());

        if (resource.exists() && resource.isReadable()) {
            logger.info("File successfully retrieved: {}", filePath);
            return resource;
        } else {
            logger.error("File exists but is not readable: {}", filePath);
            throw new ResourceNotFoundException("File does not exist or is not readable for ID: " + fileId);
        }
    }

    @Override
    @Transactional
    public void deleteAttachment(Long fileId) throws IOException {
        Optional<BomAttachment> attachmentOpt = bomAttachmentRepository.findById(fileId);

        if (attachmentOpt.isEmpty()) {
            logger.warn("File ID not found: {}", fileId);
            throw new ResourceNotFoundException("File not found with ID: " + fileId);
        }

        BomAttachment attachment = attachmentOpt.get();
        Pair<String, String> prefixAndExtension = extractBaseFilePrefixAndExtension(attachment.getFileName());

        String prefix = prefixAndExtension.getLeft();
        String extension = prefixAndExtension.getRight();

        // Find all versions with same prefix & extension
        List<BomAttachment> allVersions = bomAttachmentRepository.findAllVersionsByPrefixAndExtension(prefix, extension);

        for (BomAttachment file : allVersions) {
            Path filePath = getFilePath(file.getId());
            try {
                Files.deleteIfExists(filePath);
                logger.info("Deleted file: {}", filePath);
            } catch (IOException ex) {
                logger.error("Failed to delete file: {}. Error: {}", filePath, ex.getMessage());
            }
        }

        // Delete all versions from DB
        bomAttachmentRepository.deleteAllVersionsByPrefixAndExtension(prefix, extension);
        logger.info("Deleted all versions of file with prefix: {} and extension: {}", prefix, extension);
    }


    private Pair<String, String> extractBaseFilePrefixAndExtension(String fileName) {
        Pattern pattern = Pattern.compile("^(.*)_v\\d+\\.(\\w+)$"); // Extracts prefix and extension
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.matches()) {
            return Pair.of(matcher.group(1), matcher.group(2)); // Prefix and extension
        }
        throw new IllegalArgumentException("Invalid file name format: " + fileName);
    }


    public List<Bom> getBomByParentInventoryItem(int id){
        return bomRepository.findBomByParentInventoryItemId(id);
    }

    public WorkOrderProductionTemplate getBomWOTemplateByBomId(int id){
        return bomRepository.findWOTemplateByBomId(id);
    }

    public void recalculateAndUpdateCost(int bomId) {
        try {
            logger.info("Starting BOM cost recalculation for BOM ID: {}", bomId);

            Bom bom = bomRepository.findById(bomId)
                    .orElseThrow(() -> {
                        logger.error("BOM not found for ID: {}", bomId);
                        return new EntityNotFoundException("BOM not found for ID: " + bomId);
                    });

            WorkOrderProductionTemplate template = workOrderProductionTemplateRepository.findByBomId(bomId)
                    .orElseThrow(() -> {
                        logger.error("WorkOrderProductionTemplate not found for BOM ID: {}", bomId);
                        return new EntityNotFoundException("Template not found for BOM ID: " + bomId);
                    });

            logger.debug("Fetched BOM: {}, Template ID: {}", bom.getBomName(), template.getId());

            // Calculate estimated BOM cost
            BigDecimal estimatedBomCost = bom.getChildInventoryItems().stream()
                    .map(child -> {
                        BigDecimal unitCost = BigDecimal.valueOf(
                                inventoryItemService.getInventoryItem(
                                        child.getChildInventoryItem().getInventoryItemId()
                                ).getStandardCost()
                        );
                        return unitCost.multiply(BigDecimal.valueOf(child.getQuantity()));
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            logger.debug("Calculated estimated BOM cost: {}", estimatedBomCost);

            // Calculate labour cost
            BigDecimal labourCost = template.getWorkOrderJobLists().stream()
                    .map(job -> job.getProductionJob().getCostPerHour()
                            .multiply(job.getNumberOfHours()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            logger.debug("Calculated estimated labour cost: {}", labourCost);

            // Calculate overhead and total cost
            BigDecimal overheadPct = template.getOverheadCostPercentage() != null ? template.getOverheadCostPercentage() : BigDecimal.ZERO;
            BigDecimal overheadVal = (labourCost.add(estimatedBomCost))
                    .multiply(overheadPct.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));

            BigDecimal total = labourCost.add(estimatedBomCost).add(overheadVal);

            logger.debug("Calculated overhead: {}, Total estimated cost: {}", overheadVal, total);

            // Update template
            template.setEstimatedCostOfBom(estimatedBomCost);
            template.setEstimatedCostOfLabour(labourCost);
            template.setOverheadCostValue(overheadVal);
            template.setTotalCostOfWorkOrder(total);

            workOrderProductionTemplateRepository.save(template);

            logger.info("Successfully updated WorkOrderProductionTemplate ID: {}", template.getId());
        } catch (EntityNotFoundException e) {
            logger.warn("Recalculation skipped: {}", e.getMessage());
            throw e; // or handle gracefully
        } catch (Exception e) {
            logger.error("Error during BOM cost recalculation for BOM ID: {}", bomId, e);
            throw new RuntimeException("Failed to recalculate BOM cost", e);
        }
    }



}