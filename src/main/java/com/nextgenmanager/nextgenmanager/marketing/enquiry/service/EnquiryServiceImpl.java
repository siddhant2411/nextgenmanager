package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.repository.AppUserRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkAssignRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkDeleteRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiredProducts;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryConversationRecord;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryPriority;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryType;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryRepository;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.Quotation;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationProducts;
import com.nextgenmanager.nextgenmanager.marketing.quotation.model.QuotationStatus;
import com.nextgenmanager.nextgenmanager.marketing.quotation.repository.QuotationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Autowired
    EnquiryNumberGenerator enquiryNumberGenerator;

    @Autowired
    QuotationRepository quotationRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public Enquiry getEnquiry(Long id) {
        logger.info("Fetching Enquiry with ID: {}", id);
        try {
            Enquiry enquiry = enquiryRepository.getActiveEnquiryById(id);
            if (enquiry == null) throw new ResourceNotFoundException("Enquiry with id " + id + " not found");
            org.hibernate.Hibernate.initialize(enquiry.getEnquiredProducts());
            if (enquiry.getEnquiredProducts() != null) {
                enquiry.getEnquiredProducts().forEach(p -> org.hibernate.Hibernate.initialize(p.getInventoryItem()));
            }
            org.hibernate.Hibernate.initialize(enquiry.getEnquiryConversationRecords());
            org.hibernate.Hibernate.initialize(enquiry.getAssignedTo());
            if (enquiry.getContact() != null) {
                org.hibernate.Hibernate.initialize(enquiry.getContact());
                org.hibernate.Hibernate.initialize(enquiry.getContact().getAddresses());
                org.hibernate.Hibernate.initialize(enquiry.getContact().getPersonDetails());
            }
            return enquiry;
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
                LocalDate fetchedEnqDate = record[2] != null ? ((java.sql.Date) record[2]).toLocalDate() : null;
                String fetchedCompanyName = record[3] != null ? record[3].toString() : "N/A";
                LocalDate fetchedLastContactedDate = record[4] != null ? ((java.sql.Date) record[4]).toLocalDate() : null;
                int fetchedDaysNextToContact = record[5] != null ? (int) record[5] : 0;
                LocalDate fetchedClosedDate = null;
                if (record[6] != null) {
                    fetchedClosedDate = ((java.sql.Date) record[6]).toLocalDate();
                }

                com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus fetchedStatus = null;
                if (record[7] != null) {
                    try {
                        fetchedStatus = com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.valueOf(record[7].toString());
                    } catch (IllegalArgumentException e) {
                        logger.warn("Invalid status found: {}", record[7]);
                    }
                }
                java.math.BigDecimal fetchedExpectedRevenue = java.math.BigDecimal.ZERO;
                if (record[8] != null) {
                    fetchedExpectedRevenue = new java.math.BigDecimal(record[8].toString());
                }
                String fetchedOpportunityName = record[9] != null ? record[9].toString() : null;
                String fetchedPhone = record[10] != null ? record[10].toString() : null;
                String fetchedEmail = record[11] != null ? record[11].toString() : null;
                
                // Dynamic follow-up calculation
                LocalDate nextFollowupDate = record[16] != null ? ((java.sql.Date) record[16]).toLocalDate() : null;
                int daysRemaining = 0;
                if (nextFollowupDate != null) {
                    daysRemaining = (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), nextFollowupDate);
                } else if (record[5] != null) {
                    // Fallback to stored days if nextFollowupDate is missing
                    daysRemaining = (int) record[5];
                }

                return new EnquiryTableDTO(
                        enquiryId,
                        fetchedEnqNo,
                        fetchedEnqDate,
                        fetchedCompanyName,
                        fetchedLastContactedDate,
                        daysRemaining,
                        nextFollowupDate,
                        fetchedClosedDate,
                        fetchedStatus,
                        fetchedExpectedRevenue,
                        fetchedOpportunityName,
                        fetchedPhone,
                        fetchedEmail,
                        record[12] != null ? EnquiryPriority.valueOf(record[12].toString()) : EnquiryPriority.WARM,
                        record[15] != null ? record[15].toString() : null,
                        record[13] != null ? record[13].toString() : null,
                        record[14] != null ? record[14].toString() : null
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
    @Transactional
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
        existingEnquiry.setManualCompanyName(updatedEnquiry.getManualCompanyName());
        existingEnquiry.setContactPersonName(updatedEnquiry.getContactPersonName());
        existingEnquiry.setContactPersonPhone(updatedEnquiry.getContactPersonPhone());
        existingEnquiry.setContactPersonEmail(updatedEnquiry.getContactPersonEmail());
        existingEnquiry.setLastContactedDate(updatedEnquiry.getLastContactedDate());
        existingEnquiry.setDaysForNextFollowup(updatedEnquiry.getDaysForNextFollowup());
        existingEnquiry.setNextFollowupDate(updatedEnquiry.getNextFollowupDate());
        existingEnquiry.setEnquirySource(updatedEnquiry.getEnquirySource());
        existingEnquiry.setStatus(updatedEnquiry.getStatus());
        existingEnquiry.setOpportunityName(updatedEnquiry.getOpportunityName());
        existingEnquiry.setCloseReason(updatedEnquiry.getCloseReason());
        existingEnquiry.setClosedDate(updatedEnquiry.getClosedDate());
        existingEnquiry.setExpectedRevenue(updatedEnquiry.getExpectedRevenue());
        existingEnquiry.setProbability(updatedEnquiry.getProbability());
        existingEnquiry.setTargetCloseDate(updatedEnquiry.getTargetCloseDate());
        existingEnquiry.setPriority(updatedEnquiry.getPriority());
        existingEnquiry.setType(updatedEnquiry.getType());
        existingEnquiry.setCity(updatedEnquiry.getCity());
        existingEnquiry.setState(updatedEnquiry.getState());
        existingEnquiry.setAssignedTo(updatedEnquiry.getAssignedTo());
        existingEnquiry.setLeadQuality(updatedEnquiry.getLeadQuality());
        existingEnquiry.setDescription(updatedEnquiry.getDescription());
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
            if (newEnquiry.getEnqNo() == null || newEnquiry.getEnqNo().isBlank()) {
                newEnquiry.setEnqNo(enquiryNumberGenerator.next());
            }

            if (newEnquiry.getEnqDate() == null) {
                newEnquiry.setEnqDate(LocalDate.now());
            }

            // Set initial nextFollowupDate if not provided
            if (newEnquiry.getNextFollowupDate() == null) {
                int days = newEnquiry.getDaysForNextFollowup() > 0 ? newEnquiry.getDaysForNextFollowup() : 7;
                newEnquiry.setNextFollowupDate(newEnquiry.getEnqDate().plusDays(days));
                newEnquiry.setDaysForNextFollowup(days);
            }

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
    @Transactional
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
    @Transactional
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
    @Transactional
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

    @Override
    public com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquirySummaryDTO getEnquirySummary() {
        logger.info("Calculating Enquiry Summary");
        try {
            long totalLeads = enquiryRepository.countByDeletedDateIsNull();
            long newLeads = enquiryRepository.countByStatus(com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.NEW);
            long contacted = enquiryRepository.countByStatus(com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.CONTACTED);
            long followUp = enquiryRepository.countByStatus(com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.FOLLOW_UP);
            long won = enquiryRepository.countByStatus(com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.CONVERTED);
            long lost = enquiryRepository.countByStatus(com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.LOST);
            long overdue = enquiryRepository.countOverdueFollowups();
            java.math.BigDecimal totalRevenue = enquiryRepository.sumExpectedRevenue();
            java.math.BigDecimal wonRevenue = enquiryRepository.sumWonRevenue();

            return com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquirySummaryDTO.builder()
                    .totalLeads(totalLeads)
                    .newLeads(newLeads)
                    .contacted(contacted)
                    .followUp(followUp)
                    .won(won)
                    .lost(lost)
                    .overdueFollowups(overdue)
                    .totalExpectedRevenue(totalRevenue != null ? totalRevenue : java.math.BigDecimal.ZERO)
                    .wonRevenue(wonRevenue != null ? wonRevenue : java.math.BigDecimal.ZERO)
                    .build();
        } catch (Exception e) {
            logger.error("Error while calculating Enquiry summary: {}", e.getMessage());
            throw new RuntimeException("Failed to calculate Enquiry summary", e);
        }
    }

    @Override
    @Transactional
    public Long convertToQuotation(Long id) {
        logger.info("Converting Enquiry with ID: {} to Quotation", id);
        Enquiry enquiry = getEnquiry(id);
        if (enquiry == null) throw new ResourceNotFoundException("Enquiry not found");

        Quotation quotation = new Quotation();
        quotation.setEnquiry(enquiry);
        quotation.setQtnDate(LocalDate.now());
        quotation.setQuotationStatus(QuotationStatus.DRAFT);
        
        // Copy products
        List<QuotationProducts> qProducts = new ArrayList<>();
        if (enquiry.getEnquiredProducts() != null) {
            for (EnquiredProducts ep : enquiry.getEnquiredProducts()) {
                QuotationProducts qp = new QuotationProducts();
                qp.setInventoryItem(ep.getInventoryItem());
                qp.setProductNameRequired(ep.getProductNameRequired());
                qp.setQty(ep.getQty());
                qp.setSpecialInstruction(ep.getSpecialInstruction());
                qp.setPricePerUnit(ep.getPricePerUnit() != null ? ep.getPricePerUnit() : BigDecimal.ZERO);
                qp.setDiscountPercentage(BigDecimal.ZERO);
                qp.setQuotation(quotation);
                qProducts.add(qp);
            }
        }
        quotation.setQuotationProducts(qProducts);
        
        // Default terms and financial basics
        quotation.setGstPercentage(BigDecimal.valueOf(18)); 
        quotation.setDiscountPercentage(BigDecimal.ZERO);
        quotation.setPackagingAndForwardingChargesPercentage(BigDecimal.ZERO);
        quotation.setNetAmount(BigDecimal.ZERO);
        quotation.setTotalAmount(BigDecimal.ZERO);
        
        Quotation saved = quotationRepository.save(quotation);
        
        // Update Enquiry Status to indicate it's been quoted
        enquiry.setStatus(EnquiryStatus.QUOTED);
        enquiryRepository.save(enquiry);
        
        logger.info("Enquiry converted successfully. New Quotation ID: {}", saved.getId());
        return saved.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> getLinkedQuotations(Long enquiryId) {
        return quotationRepository.findByEnquiryId(enquiryId).stream()
                .filter(q -> q.getDeletedDate() == null)
                .map(q -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", q.getId());
                    m.put("qtnNo", q.getQtnNo());
                    m.put("qtnDate", q.getQtnDate());
                    m.put("status", q.getQuotationStatus());
                    m.put("totalAmount", q.getTotalAmount());
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public void bulkDelete(BulkDeleteRequest request) {
        logger.info("Bulk deleting {} enquiries", request.getIds().size());
        List<Enquiry> enquiries = enquiryRepository.findAllById(request.getIds());
        Date now = new Date();
        for (Enquiry e : enquiries) {
            if (e.getDeletedDate() == null) {
                e.setDeletedDate(now);
            }
        }
        enquiryRepository.saveAll(enquiries);
    }

    @Override
    @Transactional
    public void bulkAssign(BulkAssignRequest request) {
        logger.info("Bulk assigning {} enquiries to user ID {}", request.getIds().size(), request.getAssignedToId());
        AppUser user = appUserRepository.findById(request.getAssignedToId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + request.getAssignedToId()));
        List<Enquiry> enquiries = enquiryRepository.findAllById(request.getIds());
        for (Enquiry e : enquiries) {
            if (e.getDeletedDate() == null) {
                e.setAssignedTo(user);
            }
        }
        enquiryRepository.saveAll(enquiries);
    }
}
