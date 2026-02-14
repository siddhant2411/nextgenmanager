package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.*;
import com.nextgenmanager.nextgenmanager.bom.events.BomCreatedEvent;
import com.nextgenmanager.nextgenmanager.bom.events.BomModifiedEvent;
import com.nextgenmanager.nextgenmanager.bom.events.BomStatusChangedEvent;
import com.nextgenmanager.nextgenmanager.bom.mapper.BomListMapper;
import com.nextgenmanager.nextgenmanager.bom.mapper.BomMapper;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomAttachment;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.model.BomStatus;
import com.nextgenmanager.nextgenmanager.bom.repository.BomAttachmentRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.BomPositionRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.bom.spec.BomSpecifications;
import com.nextgenmanager.nextgenmanager.common.events.DomainEventPublisher;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.common.service.FileStorageService;
import com.nextgenmanager.nextgenmanager.common.spec.GenericSpecification;
import com.nextgenmanager.nextgenmanager.common.dto.FilterCriteria;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.items.service.InventoryItemService;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.service.RoutingService;
import io.minio.GetObjectResponse;
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
import org.springframework.data.jpa.domain.Specification;
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
    private BomAttachmentRepository bomAttachmentRepository;

    @Autowired
    private FileStorageService fileStorageService;


    @Autowired
    private RoutingService routingService;


    private final  DomainEventPublisher domainEventPublisher;


    private final BomListMapper bomListMapper;

    private static final String UPLOAD_DIR = "files/bom/";

    private static final Map<String, String> JOIN_FIELD_MAP = Map.of(
            "parentItemCode", "parentInventoryItem.itemCode",
            "parentItemName", "parentInventoryItem.name",
            "parentDrawingNumber", "parentInventoryItem.productSpecification.drawingNumber"
    );

    private static final Map<BomStatus, Set<BomStatus>> allowedTransitions = Map.of(
            BomStatus.DRAFT, Set.of(BomStatus.PENDING_APPROVAL,BomStatus.ARCHIVED),
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


    public List<BomPosition> getBomPositions(int bomId){
        Bom bom = getBom(bomId);
        return bom.getPositions();
    }

    public List<BomPositionDTO> getBomPositionsDTO(int bomId){
        Bom bom = getBom(bomId);
        BomDTO bomDTO = BomMapper.toDto(bom);
        return bomDTO.getPositions();
    }

    @Override
    @Transactional
    public Bom addBom(Bom bom) {

        int parentItemId = bom.getParentInventoryItem().getInventoryItemId();
        logger.debug("Creating BOM for parent item {}", parentItemId);



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

                pos.setScrapPercentage(Optional.ofNullable(pos.getScrapPercentage()).orElse(BigDecimal.ZERO));

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
    @Override
    public BomDTO changeBomStatus(int bomId, BomStatusChangeRequest bomStatusChangeRequest) {

        Bom bom = getBom(bomId);
        BomStatus currentStatus = bom.getBomStatus();
        BomStatus newBomStatus = bomStatusChangeRequest.getNextStatus();
        String approvalComment = bomStatusChangeRequest.getApprovalComments();
        String changeReason = bomStatusChangeRequest.getChangeReason();
        String ecoNumber  = bomStatusChangeRequest.getEcoNumber();

        // Validate transition
        checkBomStatusTransition(
                bom.getBomStatus(),
                newBomStatus,
                false
        );

        try {

            bom.setBomStatus(newBomStatus);
            bom.setChangeReason(changeReason);
            bom.setEcoNumber(ecoNumber);

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
                InventoryItem inventoryItem = inventoryItemService.getInventoryItem(bom.getParentInventoryItem().getInventoryItemId());
                String itemCode  = inventoryItem.getItemCode();
                Specification<Bom> spec = Specification.where(BomSpecifications.hasIsActive(true))
                        .and(Specification.where(BomSpecifications.hasParentItemCode(itemCode)));
                List<Bom> activeBOMs = bomRepository.findAll(spec);
                
                for(Bom activeBom: activeBOMs){
                    activeBom.setBomStatus(BomStatus.INACTIVE);
                    activeBom.setIsActive(false);
                    activeBom.setIsActiveVersion(false);
                    bom.setEffectiveTo(new Date());
                }
                bomRepository.saveAll(activeBOMs);
                bom.setIsActiveVersion(true);
                bom.setIsActive(true);
                bom.setBomStatus(newBomStatus);
                bom.setEffectiveFrom(new Date());
            }
            if(newBomStatus== BomStatus.INACTIVE){
                bom.setEffectiveTo(new Date());
            }

            Bom saved = bomRepository.save(bom);

            // Publish audit / lifecycle event
            domainEventPublisher.publish(new BomStatusChangedEvent(saved.getId(),currentStatus,newBomStatus));

            return BomMapper.toDto(bom);

        }

        catch (Exception e) {
            throw new RuntimeException("Failed to change BOM status", e);
        }
    }

    @Override
    public List<FileAttachment> getAttachments(int bomId) {
        List<FileAttachment> attachmentList = fileStorageService.findAttachmentsByTypeAndId("bom", (long) bomId);
        return attachmentList;
    }

    @Override
    public FileAttachment getAttachment(long fileId) {
        return fileStorageService.getFileById(fileId);
    }

    @Override
    public void uploadFile(int bomId, MultipartFile file) throws Exception {

        Bom bom = getBom(bomId);
        fileStorageService.uploadFile(file,"bom","bom",(long)bomId,"SYSTEM");

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
    public Bom editBom(int bomId,Bom bom) {
        logger.debug("Editing BOM id={}", bomId);

        // Verify BOM exists
        Bom existingBom = bomRepository.findById(bomId)
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
                pos.setScrapPercentage(Optional.ofNullable(pos.getScrapPercentage()).orElse(BigDecimal.ZERO));
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
    public void deleteAttachment(int bomId, Long fileId) throws Exception {

        fileStorageService.deleteAttachment(fileId);
        logger.info("File: {} deleted for bomId: {} ",fileId,bomId);
    }

    @Override
    public GetObjectResponse downloadBomAttachment(Long fileId) {

        try {
            GetObjectResponse file =  fileStorageService.downloadById(fileId);
            logger.info("File with id: {} requested for download",fileId);
            return file;
        }
        catch (Exception e){
            logger.error("Something went wrong while downloading file: {} "+e,fileId);
            throw new RuntimeException(e.getMessage());
        }

    }

    @Override
    public BOMRoutingMapper duplicateBom(int bomId) {
        try{
            Bom orginalBom = getBom(bomId);
            Bom bomCopy = new Bom();

            bomCopy.setBomName(orginalBom.getBomName());
            bomCopy.setParentInventoryItem(orginalBom.getParentInventoryItem());
            bomCopy.setPositions(bomCopy.getPositions());
            bomCopy.setDescription(bomCopy.getDescription());
            bomCopy.setVersionGroup(bomCopy.getVersionGroup());

            Bom newBom = addBom(bomCopy);
            orginalBom.getPositions().forEach(pos -> {
                BomPosition posCopy = new BomPosition();
                posCopy.setChildBom(pos.getChildBom());
                posCopy.setQuantity(pos.getQuantity());
                posCopy.setScrapPercentage(pos.getScrapPercentage());
                posCopy.setParentBom(newBom);
                bomPositionRepository.save(posCopy);
            });


            RoutingDto routingDto = routingService.createOrUpdateRouting(newBom.getId(),routingService.getByBom(orginalBom.getId()),"SYSTEM");
            BomDTO bomDTO = BomMapper.toDto(newBom);

            BOMRoutingMapper bomRoutingMapper = new BOMRoutingMapper();
            bomRoutingMapper.setBom(bomDTO);
            bomRoutingMapper.setRouting(routingDto);
            return bomRoutingMapper;

        }catch (ResourceNotFoundException e){
            logger.error("Bom not found with id : {} to duplicate ",bomId);
            throw new RuntimeException("Bom not found with id : "+bomId+"  to duplicate",e);
        }
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

    @Override
    public BomDTO getActiveBomByParentInventoryItem(int id){
        InventoryItem inventoryItem = inventoryItemService.getInventoryItem(id); // to check if inventory item exists
        List<Bom> boms = bomRepository.findBomByParentInventoryItemId(id);

        for(Bom bom: boms){
            if(bom.getIsActiveVersion()!=null && bom.getBomStatus()==BomStatus.ACTIVE){
                return BomMapper.toDto(bom);
            }
        }
        throw new ResourceNotFoundException("Active BOM not found for item: "+inventoryItem.getItemCode());
    }




    @Override
    public List<BomDTO> getBomHistoryByParentInventoryItem(int id){
        InventoryItem inventoryItem = inventoryItemService.getInventoryItem(id); // to check if inventory item exists
        List<Bom> boms = bomRepository.findBomByParentInventoryItemId(id);
        List<BomDTO> bomDTOS = new ArrayList<>();
        for(Bom bom: boms){
            bomDTOS.add(BomMapper.toDto(bom));
        }
        return bomDTOS;
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
        current = getBom(current.getId());
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

        logger.debug("Search text = [{}]", request);



        Specification<Bom> spec = Specification.where(BomSpecifications.hasIsActive(true))
                .and(BomSpecifications.hasIsActiveVersion(true))
                .and(BomSpecifications.isLatestVersion())
                .and(BomSpecifications.isNotDeleted());

        if (request!=null) {
            request = request.trim().replace("\"", "").toLowerCase();
            spec = spec.and(BomSpecifications.searchAcrossFields(request));
        }

        Page<Bom> boms = bomRepository.findAll(spec, pageable);
        logger.info("Bom found = [{}]", boms.getTotalElements());
        return boms.map(bomListMapper::toDTO);
    }


    @Override
    public BigDecimal calculateBomCost(int bomId) {

        Bom rootBom = getBom(bomId);  // fetch managed entity

        // Use recursion with cycle-protection to avoid infinite loops
        Set<Integer> visited = new HashSet<>();

        return calculateBomCostRecursive(rootBom, visited)
                .setScale(2, RoundingMode.HALF_UP);
    }


    private BigDecimal calculateBomCostRecursive(Bom bom, Set<Integer> visited) {

        if (bom == null) {
            return BigDecimal.ZERO;
        }

        // Detect circular BOMs
        if (visited.contains(bom.getId())) {
            throw new IllegalStateException("Circular BOM detected at BOM id: " + bom.getId());
        }

        visited.add(bom.getId());

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<BomPosition> positions = getBomPositions(bom.getId());

        for (BomPosition pos : positions) {

            BigDecimal qty = BigDecimal.valueOf(
                    Optional.of(pos.getQuantity()).orElse(1.0)
            );

            // Case 1: BOM → Inventory Item
            if (pos.getChildBom().getParentInventoryItem() != null) {

                InventoryItem item = pos.getChildBom().getParentInventoryItem();
                InventoryItem managedItem = inventoryItemService.getInventoryItem(item.getInventoryItemId());

                BigDecimal unitCost = Optional.ofNullable(managedItem.getProductFinanceSettings())
                        .map(fin -> BigDecimal.valueOf(fin.getStandardCost()))
                        .orElse(BigDecimal.ZERO);

                total = total.add(unitCost.multiply(qty));
            }

            // Case 2: BOM → Child BOM (multi-level)
            else {

                BigDecimal childBomCost = calculateBomCostRecursive(
                        pos.getChildBom(),
                        visited    // ensures cycle detection applies to full BOM tree
                );

                total = total.add(childBomCost.multiply(qty));
            }
        }

        visited.remove(bom.getId());   // allow same BOM on different branches
        return total;
    }

    @Override
    public Map<Integer, RollupRow> getRolledUpQuantity(int bomId) {

        Bom bom = getBom(bomId);

        Map<Integer, RollupRow> result = new HashMap<>();
        Set<Integer> bomPath = new HashSet<>();

        rollupRecursive(bom, 1.0, result, bomPath);

        return result;
    }

    private void rollupRecursive(
            Bom bom,
            double multiplier,
            Map<Integer, RollupRow> result,
            Set<Integer> bomPath
    ) {
        // true circular BOM protection (path-based)
        if (bomPath.contains(bom.getId())) {
            throw new IllegalStateException(
                    "Circular BOM detected at BOM id: " + bom.getId()
            );
        }

        bomPath.add(bom.getId());

        for (BomPosition pos : bom.getPositions()) {

            InventoryItem item =
                    pos.getChildBom().getParentInventoryItem();

            int itemId = item.getInventoryItemId();
            double effectiveQty = multiplier * pos.getQuantity();

            result.compute(itemId, (k, v) -> {
                if (v == null) {
                    RollupRow row = new RollupRow();
                    row.setItem(item);
                    row.setTotalQty(effectiveQty);
                    return row;
                } else {
                    v.setTotalQty(v.getTotalQty() + effectiveQty);
                    return v;
                }
            });

            // recurse into child BOM
            if (pos.getChildBom() != null) {
                rollupRecursive(
                        pos.getChildBom(),
                        effectiveQty,
                        result,
                        bomPath
                );
            }
        }

        // critical: backtrack
        bomPath.remove(bom.getId());
    }



}