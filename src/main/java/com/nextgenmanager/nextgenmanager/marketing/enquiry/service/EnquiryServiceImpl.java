package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.bom.service.InvalidDataException;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.model.AppUser;
import com.nextgenmanager.nextgenmanager.common.repository.AppUserRepository;
import com.nextgenmanager.nextgenmanager.items.model.InventoryItem;
import com.nextgenmanager.nextgenmanager.items.repository.InventoryItemRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryConversationDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryFilter;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkAssignRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkDeleteRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiredProducts;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseOutcome;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryConversationRecord;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryPriority;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryType;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryCloseReasonRepository;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.repository.EnquiryConversationRecordRepository;
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
    EnquiryCloseReasonRepository closeReasonRepository;

    @Autowired
    EnquiryConversationRecordRepository conversationRepository;

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

    /**
     * Positional indices into the native projection in EnquiryRepository.ENQUIRY_COLUMNS.
     * Named because reading record[23] and hoping is how a product summary ends up in the city.
     */
    private static final int C_ID = 0, C_ENQ_NO = 1, C_ENQ_DATE = 2, C_COMPANY = 3,
            C_LAST_CONTACTED = 4, C_DAYS_FOLLOWUP = 5, C_CLOSED_DATE = 6, C_STATUS = 7,
            C_EXPECTED_REVENUE = 8, C_OPPORTUNITY = 9, C_PHONE = 10, C_EMAIL = 11,
            C_PRIORITY = 12, C_CITY = 13, C_STATE = 14, C_ASSIGNED_NAME = 15,
            C_NEXT_FOLLOWUP = 16, C_CONTACT_ID = 17, C_ASSIGNED_ID = 18, C_SOURCE = 19,
            C_REASON_CODE = 20, C_OUTCOME = 21, C_REASON_TEXT = 22, C_PRODUCT_SUMMARY = 23,
            C_PRODUCT_COUNT = 24, C_CONVERSATION_COUNT = 25, C_LAST_CONVERSATION = 26,
            C_QUOTATION_COUNT = 27, C_PROBABILITY = 28, C_SALES_ORDER_COUNT = 29,
            C_BOOKED_AMOUNT = 30, C_AI_GENERATED = 31, C_AI_CONFIDENCE = 32,
            C_AI_REQUIRES_REVIEW = 33, C_GMAIL_THREAD_ID = 34;

    @Override
    public Page<EnquiryTableDTO> getAllActiveEnquiry(int page, int size, String sortBy, String sortDir,
                                                     EnquiryFilter filter) {
        logger.info("Fetching all active Enquiries");
        EnquiryFilter f = (filter != null ? filter : new EnquiryFilter()).normalized();

        Pageable pageable = PageRequest.of(page, size,
                sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending());

        // Enums go to the native query as their names: binding an enum to a varchar column in a
        // native query is provider-dependent, binding its name is not.
        Page<Object[]> rows = enquiryRepository.getActiveEnquiries(
                pageable,
                f.getEnqNo(), f.getEnqNoContains(), f.getCompanyName(),
                f.getStatus() != null ? f.getStatus().name() : null,
                f.getPriority() != null ? f.getPriority().name() : null,
                f.getOutcome() != null ? f.getOutcome().name() : null,
                f.getCloseReasonCode(), f.getEnquirySource(), f.getAssignedToId(),
                f.getDaysForNextFollowup(), f.getEnqDateFrom(), f.getEnqDateTo(),
                f.getLastContactedDate(), f.getEnqDate(), f.getClosedDate(),
                f.getLastContactedDateComp(), f.getEnqDateComp(), f.getClosedDateComp(),
                f.getAiGenerated(), f.getAiRequiresReview(),
                f.getGmailThreadId(), f.getGmailMessageId());

        return rows.map(this::toTableDto);
    }

    private EnquiryTableDTO toTableDto(Object[] r) {
        try {
            LocalDate nextFollowupDate = asLocalDate(r[C_NEXT_FOLLOWUP]);

            // Days remaining is recomputed from the date rather than read from the stored column,
            // which is a snapshot taken when the enquiry was last saved and goes stale overnight.
            int daysRemaining = nextFollowupDate != null
                    ? (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), nextFollowupDate)
                    : asInt(r[C_DAYS_FOLLOWUP], 0);

            return EnquiryTableDTO.builder()
                    .id(asLong(r[C_ID]))
                    .enqNo(asString(r[C_ENQ_NO]))
                    .enqDate(asLocalDate(r[C_ENQ_DATE]))
                    .companyName(r[C_COMPANY] != null ? r[C_COMPANY].toString() : "N/A")
                    .lastContactedDate(asLocalDate(r[C_LAST_CONTACTED]))
                    .daysForNextFollowup(daysRemaining)
                    .nextFollowupDate(nextFollowupDate)
                    .closedDate(asLocalDate(r[C_CLOSED_DATE]))
                    .status(asEnum(EnquiryStatus.class, r[C_STATUS], null))
                    .expectedRevenue(asBigDecimal(r[C_EXPECTED_REVENUE]))
                    .opportunityName(asString(r[C_OPPORTUNITY]))
                    .phone(asString(r[C_PHONE]))
                    .email(asString(r[C_EMAIL]))
                    .priority(asEnum(EnquiryPriority.class, r[C_PRIORITY], EnquiryPriority.WARM))
                    .assignedToName(asString(r[C_ASSIGNED_NAME]))
                    .city(asString(r[C_CITY]))
                    .state(asString(r[C_STATE]))
                    .contactId(asLong(r[C_CONTACT_ID]))
                    .assignedToId(asLong(r[C_ASSIGNED_ID]))
                    .enquirySource(asString(r[C_SOURCE]))
                    .closeReasonCode(asString(r[C_REASON_CODE]))
                    .closeOutcome(asEnum(EnquiryCloseOutcome.class, r[C_OUTCOME], null))
                    .closeReasonText(asString(r[C_REASON_TEXT]))
                    .productSummary(asString(r[C_PRODUCT_SUMMARY]))
                    .productCount(asInt(r[C_PRODUCT_COUNT], 0))
                    .conversationCount(asInt(r[C_CONVERSATION_COUNT], 0))
                    .lastConversationDate(asLocalDate(r[C_LAST_CONVERSATION]))
                    .quotationCount(asInt(r[C_QUOTATION_COUNT], 0))
                    .probability(asInt(r[C_PROBABILITY], 0))
                    .salesOrderCount(asInt(r[C_SALES_ORDER_COUNT], 0))
                    .bookedAmount(asBigDecimal(r[C_BOOKED_AMOUNT]))
                    .aiGenerated(asBoolean(r[C_AI_GENERATED]))
                    .aiConfidence(asBigDecimal(r[C_AI_CONFIDENCE]))
                    .aiRequiresReview(asBoolean(r[C_AI_REQUIRES_REVIEW]))
                    .gmailThreadId(asString(r[C_GMAIL_THREAD_ID]))
                    .build();
        } catch (Exception e) {
            logger.error("Error mapping enquiry row: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    // JDBC hands back whatever the driver felt like for a given column type, so the projection is
    // read through converters rather than casts. A ClassCastException here would take down the
    // whole list page for one unexpected column type.

    private static String asString(Object v) {
        return v != null ? v.toString() : null;
    }

    private static Long asLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    // Postgres hands a BOOLEAN back as Boolean, but the same projection read through H2 in a test
    // arrives as a Number, and some drivers give the string. All three mean the same thing.
    private static Boolean asBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(v.toString());
    }

    private static int asInt(Object v, int fallback) {
        return v instanceof Number n ? n.intValue() : fallback;
    }

    private static BigDecimal asBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        return new BigDecimal(v.toString());
    }

    private static LocalDate asLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (v instanceof java.util.Date d) return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return LocalDate.parse(v.toString());
    }

    private static <T extends Enum<T>> T asEnum(Class<T> type, Object v, T fallback) {
        if (v == null) return fallback;
        try {
            return Enum.valueOf(type, v.toString());
        } catch (IllegalArgumentException e) {
            logger.warn("Unrecognised {} value in enquiry row: {}", type.getSimpleName(), v);
            return fallback;
        }
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
        existingEnquiry.setCloseReasonCode(resolveCloseReason(updatedEnquiry.getCloseReasonCode()));
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

    /**
     * The close-reason screen posts the whole master row back, so what arrives is a detached copy
     * that may carry a stale description or outcome. Only its identity is trusted; the row itself
     * is re-read from the master. A code that does not resolve is rejected rather than dropped --
     * silently ignoring it is how an enquiry ends up closed with no reportable reason.
     */
    private com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseReason resolveCloseReason(
            com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseReason submitted) {
        if (submitted == null) return null;
        if (submitted.getId() != null) {
            return closeReasonRepository.findById(submitted.getId())
                    .orElseThrow(() -> new InvalidDataException("Unknown close reason id: " + submitted.getId()));
        }
        if (submitted.getCode() != null && !submitted.getCode().isBlank()) {
            return closeReasonRepository.findByCodeIgnoreCase(submitted.getCode().trim())
                    .orElseThrow(() -> new InvalidDataException("Unknown close reason code: " + submitted.getCode()));
        }
        return null;
    }

    private void updateConversationRecords(Enquiry existingEnquiry, List<EnquiryConversationRecord> updatedRecords) {
        existingEnquiry.getEnquiryConversationRecords().clear();

        LocalDate latest = null;
        if (updatedRecords != null) {
            for (EnquiryConversationRecord record : updatedRecords) {
                record.setEnquiry(existingEnquiry);
                // A record saved through the enquiry screen happened today unless it says otherwise.
                if (record.getConversationDate() == null) {
                    record.setConversationDate(LocalDate.now());
                }
                if (record.getDeletedDate() == null) {
                    LocalDate on = effectiveDate(record);
                    if (on != null && (latest == null || on.isAfter(latest))) latest = on;
                }
                existingEnquiry.getEnquiryConversationRecords().add(record);
            }
        }

        // When there is a log, the log wins: a caller that edits the conversation list and leaves
        // lastContactedDate alone would otherwise leave the two contradicting each other, and
        // lastContactedDate is what the follow-up reporting reads. An empty log keeps whatever
        // updateBasicFields already set, so a date typed by hand on an enquiry with no recorded
        // conversations is not silently erased.
        if (latest != null) {
            existingEnquiry.setLastContactedDate(latest);
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

            newEnquiry.setCloseReasonCode(resolveCloseReason(newEnquiry.getCloseReasonCode()));

            // Same rule as the update path: conversations dated from the log, lastContactedDate
            // derived from it rather than taken on trust.
            if (newEnquiry.getEnquiryConversationRecords() != null) {
                LocalDate latest = null;
                for (EnquiryConversationRecord record : newEnquiry.getEnquiryConversationRecords()) {
                    record.setEnquiry(newEnquiry);
                    if (record.getConversationDate() == null) {
                        record.setConversationDate(LocalDate.now());
                    }
                    LocalDate on = effectiveDate(record);
                    if (on != null && (latest == null || on.isAfter(latest))) latest = on;
                }
                if (latest != null) newEnquiry.setLastContactedDate(latest);
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
                // The UI sends a code from the close-reason master; anything else is still
                // accepted and kept as free text, so older callers do not break.
                if (closeReason != null && !closeReason.isBlank()) {
                    closeReasonRepository.findByCodeIgnoreCase(closeReason.trim())
                            .ifPresent(enquiry::setCloseReasonCode);
                }
                enquiryRepository.save(enquiry);
            }
        } catch (Exception e) {
            logger.error("Error while closing enquiry with id: {}", id);
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public Enquiry applyAiReview(Long id,
            com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.AiReviewDecisionDTO decision) {
        if (decision == null || decision.getDecision() == null) {
            throw new InvalidDataException("decision is required and must be ACCEPT or REJECT");
        }

        Enquiry enquiry = enquiryRepository.getActiveEnquiryById(id);
        if (enquiry == null) {
            throw new ResourceNotFoundException("Enquiry with id:" + id + " does not exist");
        }
        // Reviewing something no machine wrote is a client bug, not a no-op to swallow: it means
        // the desk is pointed at the wrong row, and clearing a flag that was never set would hide
        // that.
        if (!enquiry.isAiGenerated()) {
            throw new InvalidDataException(
                    "Enquiry " + enquiry.getEnqNo() + " was not raised by the AI Lead Agent");
        }

        boolean reject = decision.getDecision()
                == com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.AiReviewDecisionDTO.Decision.REJECT;

        // Decided either way. The flag means "waiting on a human", and a human has now answered.
        enquiry.setAiRequiresReview(false);
        if (reject) {
            enquiry.setStatus(com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus.JUNK);
        }

        // The verdict goes in the conversation trail rather than only into the flag, so six months
        // later the register still says who accepted the lead and why.
        EnquiryConversationRecord record = new EnquiryConversationRecord();
        record.setEnquiry(enquiry);
        record.setConversationType(EnquiryConversationRecord.ConversationType.NOTE);
        record.setConversationDate(LocalDate.now());
        String notes = decision.getNotes() != null ? decision.getNotes().trim() : "";
        record.setConversation("AI lead review: " + decision.getDecision()
                + (notes.isEmpty() ? "" : " — " + notes));
        conversationRepository.save(record);

        return enquiryRepository.save(enquiry);
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


    // ------------------------------------------------------------------ conversation log

    @Override
    @Transactional(readOnly = true)
    public List<EnquiryConversationDTO> getConversations(Long enquiryId) {
        requireEnquiry(enquiryId);
        return conversationRepository.findActiveByEnquiryId(enquiryId).stream()
                .map(EnquiryConversationDTO::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EnquiryConversationDTO addConversation(Long enquiryId, EnquiryConversationDTO request) {
        Enquiry enquiry = requireEnquiry(enquiryId);
        if (request == null || request.getConversation() == null || request.getConversation().isBlank()) {
            throw new InvalidDataException("conversation text is required");
        }

        EnquiryConversationRecord record = new EnquiryConversationRecord();
        record.setEnquiry(enquiry);
        record.setConversation(request.getConversation().trim());
        record.setConversationType(request.getConversationType() != null
                ? request.getConversationType()
                : EnquiryConversationRecord.ConversationType.NOTE);
        record.setConversationDate(request.getConversationDate() != null
                ? request.getConversationDate()
                : LocalDate.now());
        EnquiryConversationRecord saved = conversationRepository.save(record);

        touchLastContacted(enquiry);
        enquiryRepository.save(enquiry);

        return EnquiryConversationDTO.from(saved);
    }

    @Override
    @Transactional
    public void deleteConversation(Long enquiryId, Long conversationId) {
        Enquiry enquiry = requireEnquiry(enquiryId);
        EnquiryConversationRecord record = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation " + conversationId + " not found"));
        if (record.getEnquiry() == null || !enquiryId.equals(record.getEnquiry().getId())) {
            throw new ResourceNotFoundException(
                    "Conversation " + conversationId + " does not belong to enquiry " + enquiryId);
        }
        record.setDeletedDate(new Date());
        conversationRepository.save(record);

        // Deleting the newest entry has to walk lastContactedDate back, or the enquiry keeps
        // claiming a contact that no longer has a record behind it.
        touchLastContacted(enquiry);
        enquiryRepository.save(enquiry);
    }

    /**
     * Sets lastContactedDate from the conversation log rather than trusting a caller to maintain
     * it. The column existed from the start and nothing ever wrote to it, which is why the
     * register could not answer how many of the enquiries that went silent were ever chased.
     */
    private void touchLastContacted(Enquiry enquiry) {
        conversationRepository.findActiveByEnquiryId(enquiry.getId()).stream()
                .map(EnquiryServiceImpl::effectiveDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .ifPresentOrElse(enquiry::setLastContactedDate, () -> enquiry.setLastContactedDate(null));
    }

    private static LocalDate effectiveDate(EnquiryConversationRecord r) {
        if (r.getConversationDate() != null) return r.getConversationDate();
        if (r.getCreationDate() != null) {
            return r.getCreationDate().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private Enquiry requireEnquiry(Long enquiryId) {
        Enquiry enquiry = enquiryRepository.getActiveEnquiryById(enquiryId);
        if (enquiry == null) {
            throw new ResourceNotFoundException("Enquiry with id " + enquiryId + " not found");
        }
        return enquiry;
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
