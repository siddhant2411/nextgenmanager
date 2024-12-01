package com.nextgenmanager.nextgenmanager.bom.service;

import com.nextgenmanager.nextgenmanager.bom.dto.BomDTO;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.model.BomPosition;
import com.nextgenmanager.nextgenmanager.bom.repository.BomPositionRepository;
import com.nextgenmanager.nextgenmanager.bom.repository.BomRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

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

}