package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiredProducts;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryConversationRecord;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EnquiryServiceImpl implements EnquiryService {

    private static final Logger logger = LoggerFactory.getLogger(EnquiryServiceImpl.class);

    @Autowired
    EnquiryRepository enquiryRepository;

    @Autowired
    InventoryItemRepository inventoryItemRepository;

    @Override
    public Enquiry getEnquiry(Long id) {
        logger.info("Fetching Enquiry with ID: {}", id);
        try {
            return enquiryRepository.getActiveEnquiryById(id);
        } catch (ResourceNotFoundException e) {
            logger.error("Enquiry with id {} not found", id);
            throw e;
        } catch (Exception e) {
            logger.error("Error while fetching Enquiry with ID {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to fetch Enquiry", e);
        }
    }

    @Override
    public Page<EnquiryTableDTO> getAllActiveEnquiry(int page, int size, String sortBy, String sortDir, String enqNo, String companyName, LocalDate lastContactedDate,
                                                     LocalDate enqDate, LocalDate closedDate, Integer daysForNetFollowUp,
                                                     String dateComparisonTypeLastContacted,
                                                     String dateComparisonTypeEnqDate,
                                                     String dateComparisonTypeClosedDate) {
        logger.info("Fetching all active Enquiries");
        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

        Page<Object[]> allActiveEnquires = enquiryRepository.getActiveEnquiries(pageable, enqNo, companyName, lastContactedDate, daysForNetFollowUp, enqDate,
                closedDate, dateComparisonTypeLastContacted, dateComparisonTypeEnqDate, dateComparisonTypeClosedDate);

        return allActiveEnquires.map(record -> {
            try {
                Long enquiryId = ((Number) record[0]).longValue();
                String fetchedEnqNo = record[1].toString();
                LocalDate fetchedEnqDate = ((java.sql.Date) record[2]).toLocalDate();
                String fetchedCompanyName = record[3].toString();
                LocalDate fetchedLastContactedDate = ((java.sql.Date) record[4]).toLocalDate();
                int fetchedDaysNextToContact = (int) record[5];
                LocalDate fetchedClosedDate = null;
                if (record[6] != null) {
                    fetchedClosedDate = ((java.sql.Date) record[6]).toLocalDate();
                }

                com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus fetchedStatus = null;
                if (record[7] != null) {
                    fetchedStatus = com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.valueOf(record[7].toString());
                }
                java.math.BigDecimal fetchedExpectedRevenue = java.math.BigDecimal.ZERO;
                if (record[8] != null) {
                    fetchedExpectedRevenue = new java.math.BigDecimal(record[8].toString());
                }
                String fetchedOpportunityName = null;
                if (record[9] != null) {
                    fetchedOpportunityName = record[9].toString();
                }

                return new EnquiryTableDTO(
                        enquiryId,
                        fetchedEnqNo,
                        fetchedEnqDate,
                        fetchedCompanyName,
                        fetchedLastContactedDate,
                        fetchedDaysNextToContact,
                        fetchedClosedDate,
                        fetchedStatus,
                        fetchedExpectedRevenue,
                        fetchedOpportunityName
                );
            } catch (Exception e) {
                logger.error("Error mapping enquiry data: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Page<Enquiry> getAllEnquiry(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

        return enquiryRepository.findAll(pageable);
    }

    @Override
    public Enquiry updateEnquiry(Enquiry updatedEnquiry, Long id) {
        logger.info("Updating Enquiry with ID: {}", id);
        Enquiry existingEnquiry = getEnquiry(id);
        try {
            updateBasicFields(existingEnquiry, updatedEnquiry);
            updateConversationRecords(existingEnquiry, updatedEnquiry.getEnquiryConversationRecords());
            updateEnquiredProducts(existingEnquiry, updatedEnquiry.getEnquiredProducts());

            return enquiryRepository.save(existingEnquiry);
        } catch (ResourceNotFoundException e) {
            logger.error("Enquiry not found with ID: {}", id);
            throw e;
        } catch (Exception e) {
            logger.error("Error while updating Enquiry with ID: {}", id, e);
            throw new RuntimeException("Failed to update Enquiry", e);
        }
    }

    private void updateBasicFields(Enquiry existingEnquiry, Enquiry updatedEnquiry) {
        existingEnquiry.setEnqDate(updatedEnquiry.getEnqDate());
        existingEnquiry.setContact(updatedEnquiry.getContact());
        existingEnquiry.setLastContactedDate(updatedEnquiry.getLastContactedDate());
        existingEnquiry.setDaysForNextFollowup(updatedEnquiry.getDaysForNextFollowup());
        existingEnquiry.setEnquirySource(updatedEnquiry.getEnquirySource());
        existingEnquiry.setStatus(updatedEnquiry.getStatus());
        existingEnquiry.setOpportunityName(updatedEnquiry.getOpportunityName());
        existingEnquiry.setCloseReason(updatedEnquiry.getCloseReason());
        existingEnquiry.setClosedDate(updatedEnquiry.getClosedDate());
        existingEnquiry.setExpectedRevenue(updatedEnquiry.getExpectedRevenue());
        existingEnquiry.setProbability(updatedEnquiry.getProbability());
        existingEnquiry.setTargetCloseDate(updatedEnquiry.getTargetCloseDate());
    }

    private void updateConversationRecords(Enquiry existingEnquiry, List<EnquiryConversationRecord> updatedRecords) {
        existingEnquiry.getEnquiryConversationRecords().clear();

        if (updatedRecords != null) {
            for (EnquiryConversationRecord record : updatedRecords) {
                record.setEnquiry(existingEnquiry);
                existingEnquiry.getEnquiryConversationRecords().add(record);
            }
        }
    }

    private void updateEnquiredProducts(Enquiry existingEnquiry, List<EnquiredProducts> updatedProducts) {
        Set<Long> updatedProductIds = updatedProducts.stream()
                .map(EnquiredProducts::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        existingEnquiry.getEnquiredProducts().removeIf(product -> !updatedProductIds.contains(product.getId()));

        if (updatedProducts != null) {
            for (EnquiredProducts product : updatedProducts) {
                if (product.getId() != null && product.getId() > 0) {
                    existingEnquiry.getEnquiredProducts().stream()
                            .filter(p -> p.getId() == product.getId())
                            .findFirst()
                            .ifPresent(existingProduct -> {
                                if (product.getInventoryItem() != null && product.getInventoryItem().getInventoryItemId() >= 0) {
                                    InventoryItem managedInventoryItem = inventoryItemRepository
                                            .findById(product.getInventoryItem().getInventoryItemId())
                                            .orElseThrow(() -> new ResourceNotFoundException("InventoryItem not found"));
                                    existingProduct.setInventoryItem(managedInventoryItem);
                                }
                                existingProduct.setQty(product.getQty());
                                existingProduct.setSpecialInstruction(product.getSpecialInstruction());
                            });

                } else {
                    if (product.getInventoryItem() != null && product.getInventoryItem().getInventoryItemId() > 0) {
                        InventoryItem managedInventoryItem = inventoryItemRepository
                                .findById(product.getInventoryItem().getInventoryItemId())
                                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem not found"));
                        product.setInventoryItem(managedInventoryItem);
                    } else {
                        product.setInventoryItem(null);
                    }
                    existingEnquiry.getEnquiredProducts().add(product);
                }
            }
        }
    }

    @Override
    public Enquiry createEnquiry(Enquiry newEnquiry) {
        try {
            for (EnquiredProducts enquiredProduct : newEnquiry.getEnquiredProducts()) {
                if (enquiredProduct.getInventoryItem() != null && enquiredProduct.getInventoryItem().getInventoryItemId() <= 0) {
                    enquiredProduct.setInventoryItem(null);
                }
            }
            return enquiryRepository.save(newEnquiry);
        } catch (Exception e) {
            logger.error("Error creating enquiry: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create enquiry.");
        }
    }

    @Override
    public void deleteEnquiry(Long id) {
        logger.info("Deleting Enquiry with ID: {}", id);
        Enquiry enquiry = enquiryRepository.getActiveEnquiryById(id);
        try {
            if (enquiry == null) {
                throw new ResourceNotFoundException("Enquiry with id:" + id + " either already deleted or does not exist");
            } else {
                enquiry.setDeletedDate(new Date());
                enquiryRepository.save(enquiry);
            }
        } catch (Exception e) {
            logger.error("Error while deleting enquiry with id: {}", id);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void closeEnquiry(Long id, String closeReason) {
        logger.info("Closing Enquiry with ID: {}", id);
        Enquiry enquiry = enquiryRepository.getActiveEnquiryById(id);
        try {
            if (enquiry == null) {
                throw new ResourceNotFoundException("Enquiry with id:" + id + " either already deleted or does not exist");
            } else {
                enquiry.setClosedDate(LocalDate.now());
                enquiry.setCloseReason(closeReason);
                enquiryRepository.save(enquiry);
            }
        } catch (Exception e) {
            logger.error("Error while closing enquiry with id: {}", id);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateEnquiryStatus(Long id, com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus status) {
        logger.info("Updating status for Enquiry with ID: {}", id);
        Enquiry enquiry = enquiryRepository.getActiveEnquiryById(id);
        try {
            if (enquiry == null) {
                throw new ResourceNotFoundException("Enquiry with id:" + id + " does not exist");
            }
            enquiry.setStatus(status);
            if (status == com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.CLOSED || status == com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.LOST) {
                if (enquiry.getClosedDate() == null) {
                    enquiry.setClosedDate(LocalDate.now());
                }
            } else {
                enquiry.setClosedDate(null);
                enquiry.setCloseReason(null);
            }
            enquiryRepository.save(enquiry);
        } catch (Exception e) {
            logger.error("Error while updating status for enquiry with id: {}", id);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Enquiry getEnquiryByEnquiryNo(String enqNo) {
        logger.info("Fetching Enquiry with enqNo: {}", enqNo);
        try {
            return enquiryRepository.findByEnqNo(enqNo)
                    .orElseThrow(() -> new EntityNotFoundException("Enquiry not found"));
        } catch (EntityNotFoundException e) {
            logger.error("Enquiry with enqNo {} not found", enqNo);
            throw e;
        } catch (Exception e) {
            logger.error("Error while fetching Enquiry with enqNo {}: {}", enqNo, e.getMessage());
            throw new RuntimeException("Failed to fetch Enquiry", e);
        }
    }
}

