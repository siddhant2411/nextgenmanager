package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomListDTO;
import com.nextgenmanager.nextgenmanager.bom.events.BomCreatedEvent;
import com.nextgenmanager.nextgenmanager.bom.events.BomModifiedEvent;
import com.nextgenmanager.nextgenmanager.bom.events.BomStatusChangedEvent;
import com.nextgenmanager.nextgenmanager.bom.mapper.BomListMapper;
import com.nextgenmanager.nextgenmanager.bom.mapper.BomMapper;
import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomAttachment;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import com.nextgenmanager.nextgenmanager.bom.repository.BomAttachmentRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.BomPositionRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.bom.spec.BomSpecifications;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.common.spec.GenericSpecification;
import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.production.model.WorkOrderProductionTemplate;
import com.nextgenmanager.nextgenmanager.production.repository.WorkOrderProductionTemplateRepository;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    private final  DomainEventPublisher domainEventPublisher;


    private final BomListMapper bomListMapper;

    private static final String UPLOAD_DIR = "files/bom/";

    private static final Map<String, String> JOIN_FIELD_MAP = Map.of(
            "parentItemCode", "parentInventoryItem.itemCode",
            "parentItemName", "parentInventoryItem.name",
            "parentDrawingNumber", "parentInventoryItem.productSpecification.drawingNumber"
    );

    private static final Map<BomStatus, Set<BomStatus>> allowedTransitions = Map.of(
            BomStatus.DRAFT, Set.of(BomStatus.PENDING_APPROVAL),
            BomStatus.PENDING_APPROVAL, Set.of(BomStatus.APPROVED),
            BomStatus.APPROVED, Set.of(BomStatus.ACTIVE),
            BomStatus.ACTIVE, Set.of(BomStatus.INACTIVE, BomStatus.OBSOLETE),
            BomStatus.INACTIVE, Set.of(BomStatus.ACTIVE),
            BomStatus.OBSOLETE, Set.of(BomStatus.ARCHIVED),
            BomStatus.ARCHIVED, Set.of()
    );

    public BomServiceImpl(DomainEventPublisher domainEventPublisher, BomListMapper bomListMapper) {
        this.domainEventPublisher = domainEventPublisher;
        this.bomListMapper = bomListMapper;
    }


    @Override
    @Transactional
    public Bom addBom(Bom bom) {

        int parentItemId = bom.getParentInventoryItem().getInventoryItemId();
        logger.debug("Creating BOM for parent item {}", parentItemId);


        if (!bomRepository.findBomByParentInventoryItemId(parentItemId).isEmpty()) {
            throw new BusinessException(
                    "A BOM already exists for item: " + bom.getParentInventoryItem().getItemCode() +
                            ". Create a new BOM version via approval workflow."
            );
        }

        // Validate parent exists
        inventoryItemRepository.findById(parentItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent inventory item not found"));

        // Validate positions
        if (bom.getPositions() != null) {
            for (BomPosition pos : bom.getPositions()) {

                if (pos.getChildBom() == null) {
                    throw new InvalidDataException("Child BOM cannot be null");
                }

                if (pos.getQuantity() <= 0) {
                    throw new InvalidDataException("Quantity must be > 0");
                }

                if (checkForCycle(bom, pos.getChildBom())) {
                    throw new InvalidDataException("BOM cycle detected");
                }

                pos.setParentBom(bom);
            }
        }

        // Initial BOM setup (PLM standard)
        bom.setVersionNumber(0);            // Real version assigned on APPROVED
        bom.setRevision("R0");
        bom.setIsActiveVersion(false);
        bom.setBomStatus(BomStatus.DRAFT);
        bom.setVersionGroup(String.valueOf(parentItemId));

        Bom savedBom = bomRepository.save(bom);
        domainEventPublisher.publish(
                new BomCreatedEvent(
                        savedBom.getId(),
                        savedBom.getParentInventoryItem().getInventoryItemId(),
                        savedBom.getVersionNumber(),
                        savedBom.getRevision(),
                        savedBom.getBomStatus()
                )
        );


        logger.info("Created new draft BOM id={} for item {}", savedBom.getId(), parentItemId);

        return savedBom;
    }



    public void checkBomStatusTransition(BomStatus current, BomStatus newStatus, boolean adminOverride) {

        // ADMIN/SYSTEM ARCHIVE – allowed from ANY state
        if (newStatus == BomStatus.ARCHIVED && adminOverride) {
            return;
        }

        Set<BomStatus> allowed = allowedTransitions.getOrDefault(current, Set.of());

        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    "Invalid BOM transition from " + current + " to " + newStatus
            );
        }
    }


    @Transactional
    public Bom changeBomStatus(int bomId, BomStatus newBomStatus, String approvalComment) {

        Bom bom = getBom(bomId);
        BomStatus currentStatus = bom.getBomStatus();

        if (!bom.getIsActive()) {
            throw new IllegalStateException(
                    "Cannot change state of inactive BOM: " + bom.getId()
            );
        }

        // Validate transition
        checkBomStatusTransition(
                bom.getBomStatus(),
                newBomStatus,
                false
        );

        try {

            bom.setBomStatus(newBomStatus);

            // Versioning logic
            if (newBomStatus == BomStatus.APPROVED) {

                int maxVersion = bomRepository
                        .findMaxVersionNumber(bom.getParentInventoryItem().getInventoryItemId());

                int nextVersion = maxVersion + 1;
                bom.setVersionNumber(nextVersion);
                bom.setRevision(toRevision(nextVersion));
                bom.setApprovedBy("SYSTEM");
                bom.setApprovalComments(approvalComment);
                bom.setApprovalDate(new Date());
            }

            if(newBomStatus == BomStatus.ACTIVE){
                bom.setIsActiveVersion(true);
                bom.setEffectiveFrom(new Date());
            }
            if(newBomStatus==BomStatus.INACTIVE){
                bom.setEffectiveTo(new Date());
            }

            Bom saved = bomRepository.save(bom);

            // Publish audit / lifecycle event
            domainEventPublisher.publish(new BomStatusChangedEvent(saved.getId(),currentStatus,newBomStatus));

            return saved;

        } catch (Exception e) {
            throw new RuntimeException("Failed to change BOM status", e);
        }
    }


    public static String toRevision(int versionNumber) {
        StringBuilder sb = new StringBuilder();
        int num = versionNumber;

        while (num > 0) {
            num--;  // Adjust because A=1 not A=0
            int remainder = num % 26;
            sb.append((char) ('A' + remainder));
            num /= 26;
        }

        return sb.reverse().toString();
    }

    public static String nextRevision(String current) {
        int num = fromRevision(current);
        return toRevision(num + 1);
    }

    public static int fromRevision(String rev) {
        int num = 0;
        for (int i = 0; i < rev.length(); i++) {
            num = num * 26 + (rev.charAt(i) - 'A' + 1);
        }
        return num;
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
                .orElseThrow(() -> {
                    logger.error("BOM not found or deleted with ID: {}", id);
                    return new ResourceNotFoundException("BOM not found with ID: " + id);
                });
    }

    @Override
    public BomDTO getBomDTO(int id) {
        logger.info("Fetching BOM DTO with ID: {}", id);

        Bom bom = bomRepository.findById(id)
                .filter(b -> b.getDeletedDate() == null)
                .orElseThrow(() -> {
                    logger.error("BOM not found or deleted with ID: {}", id);
                    return new ResourceNotFoundException("BOM not found with ID: " + id);
                });

        // Ensure required lazy relations are initialized
        Hibernate.initialize(bom.getParentInventoryItem());
//        TODO:  Fix this with BOM
//        Hibernate.initialize(bom.getChildInventoryItems());
        Hibernate.initialize(bom.getBomAttachmentList());

        // Fetch latest attachments before mapping
        List<BomAttachment> latestAttachments = getLatestAttachments(bom);
        bom.setBomAttachmentList(latestAttachments);

        // Map entity to DTO
        BomDTO response = BomMapper.toDto(bom);

        logger.info("Successfully mapped BOM with ID {} to DTO", id);
        return response;
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
    @Transactional
    public Bom editBom(Bom bom) {
        logger.debug("Editing BOM id={}", bom.getId());

        // Verify BOM exists
        Bom existingBom = bomRepository.findById(bom.getId())
                .orElseThrow(() -> new ResourceNotFoundException("BOM not found with ID: " + bom.getId()));

        // Only editable in DRAFT or PENDING_APPROVAL
        if (existingBom.getBomStatus() != BomStatus.DRAFT &&
                existingBom.getBomStatus() != BomStatus.PENDING_APPROVAL) {
            throw new InvalidDataException(
                    "Only DRAFT or PENDING_APPROVAL BOMs can be edited. Current status: " +
                            existingBom.getBomStatus()
            );
        }

        // Validate parent item
        int parentId = bom.getParentInventoryItem().getInventoryItemId();
        if (!inventoryItemRepository.existsById(parentId)) {
            throw new ResourceNotFoundException("Parent inventory item not found");
        }

        // Validate positions
        if (bom.getPositions() != null) {
            for (BomPosition pos : bom.getPositions()) {

                if (pos.getChildBom() == null) {
                    throw new InvalidDataException("Child BOM cannot be null");
                }

                if (pos.getQuantity() <= 0) {
                    throw new InvalidDataException("Quantity must be > 0");
                }

                if (checkForCycle(bom, pos.getChildBom())) {
                    throw new InvalidDataException("BOM cycle detected");
                }

                pos.setParentBom(bom);
            }
        }


        bom.setVersionGroup(existingBom.getVersionGroup());
        bom.setVersionNumber(existingBom.getVersionNumber());
        bom.setRevision(existingBom.getRevision());
        bom.setBomStatus(existingBom.getBomStatus());
        bom.setIsActiveVersion(existingBom.getIsActiveVersion());

        Bom saved = bomRepository.save(bom);

        // Publish modification event
        domainEventPublisher.publish(
                new BomModifiedEvent(
                        saved.getId(),
                        saved.getVersionNumber(),
                        saved.getRevision(),
                        saved.getBomStatus()
                )
        );

        logger.info("Updated BOM id={} (still in {} status)", saved.getId(), saved.getBomStatus());

        return saved;
    }


    @Override
    public Page<Bom> getAllBom(int page, int size, String sortBy, String sortDir, String query) {
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
            Page<Bom> boms = bomRepository.findAllActiveBom( pageable);

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

//    public void recalculateAndUpdateCost(int bomId) {
//        try {
//            logger.info("Starting BOM cost recalculation for BOM ID: {}", bomId);
//
//            Bom bom = bomRepository.findById(bomId)
//                    .orElseThrow(() -> {
//                        logger.error("BOM not found for ID: {}", bomId);
//                        return new EntityNotFoundException("BOM not found for ID: " + bomId);
//                    });
//
//            WorkOrderProductionTemplate template = workOrderProductionTemplateRepository.findByBomId(bomId)
//                    .orElseThrow(() -> {
//                        logger.error("WorkOrderProductionTemplate not found for BOM ID: {}", bomId);
//                        return new EntityNotFoundException("Template not found for BOM ID: " + bomId);
//                    });
//
//            logger.debug("Fetched BOM: {}, Template ID: {}", bom.getBomName(), template.getId());
//
//            // Calculate estimated BOM cost
//            BigDecimal estimatedBomCost = bom.getChildInventoryItems().stream()
//                    .map(child -> {
//                        BigDecimal unitCost = BigDecimal.valueOf(
//                                inventoryItemService.getInventoryItem(
//                                        child.getChildInventoryItem().getInventoryItemId()
//                                ).getProductFinanceSettings().getStandardCost()
//                        );
//                        return unitCost.multiply(BigDecimal.valueOf(child.getQuantity()));
//                    })
//                    .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//            logger.debug("Calculated estimated BOM cost: {}", estimatedBomCost);
//
//            // Calculate labour cost
//            BigDecimal labourCost = template.getWorkOrderJobLists().stream()
//                    .map(job -> job.getProductionJob().getCostPerHour()
//                            .multiply(job.getNumberOfHours()))
//                    .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//            logger.debug("Calculated estimated labour cost: {}", labourCost);
//
//            // Calculate overhead and total cost
//            BigDecimal overheadPct = template.getOverheadCostPercentage() != null ? template.getOverheadCostPercentage() : BigDecimal.ZERO;
//            BigDecimal overheadVal = (labourCost.add(estimatedBomCost))
//                    .multiply(overheadPct.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
//
//            BigDecimal total = labourCost.add(estimatedBomCost).add(overheadVal);
//
//            logger.debug("Calculated overhead: {}, Total estimated cost: {}", overheadVal, total);
//
//            // Update template
//            template.setEstimatedCostOfBom(estimatedBomCost);
//            template.setEstimatedCostOfLabour(labourCost);
//            template.setOverheadCostValue(overheadVal);
//            template.setTotalCostOfWorkOrder(total);
//
//            workOrderProductionTemplateRepository.save(template);
//
//            logger.info("Successfully updated WorkOrderProductionTemplate ID: {}", template.getId());
//        } catch (EntityNotFoundException e) {
//            logger.warn("Recalculation skipped: {}", e.getMessage());
//            throw e; // or handle gracefully
//        } catch (Exception e) {
//            logger.error("Error during BOM cost recalculation for BOM ID: {}", bomId, e);
//            throw new RuntimeException("Failed to recalculate BOM cost", e);
//        }
//    }


    public Page<BomListDTO> filterBom(FilterRequest request){
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

        Specification<Bom> spec = GenericSpecification.buildSpecification(filters, JOIN_FIELD_MAP);
        Page<Bom> boms = bomRepository.findAll(spec, pageable);

        return boms.map(bomListMapper::toDTO);

    }


    public boolean checkForCycle(Bom parent, Bom child) {
        return isDescendant(child, parent);
    }

    private boolean isDescendant(Bom current, Bom target) {
        if (current.getId() == target.getId()) {
            return true;   // cycle
        }

        if (current.getPositions() == null) return false;

        for (BomPosition pos : current.getPositions()) {
            Bom childBom = pos.getChildBom();
            if (childBom != null && isDescendant(childBom, target)) {
                return true;
            }
        }

        return false;
    }


    public Page<BomListDTO> searchActiveBom(String request) {
        Sort sort = Sort.by(Sort.Direction.ASC, "parentInventoryItem.name");
        Pageable pageable = PageRequest.of(0, 10, sort);

        logger.info("Search text = [{}]", request);



        Specification<Bom> spec = Specification.where(BomSpecifications.hasIsActive(true))
                .and(BomSpecifications.hasIsActiveVersion(true))
                .and(BomSpecifications.isLatestVersion());

        if (request!=null) {
            request = request.trim().replace("\"", "").toLowerCase();
            spec = spec.and(BomSpecifications.searchAcrossFields(request));
        }

        Page<Bom> boms = bomRepository.findAll(spec, pageable);
        logger.info("Bom found = [{}]", boms.getTotalElements());
        return boms.map(bomListMapper::toDTO);
    }




}