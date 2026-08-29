package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryConversationDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryFilter;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkAssignRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkDeleteRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface EnquiryService {

    Enquiry getEnquiry(Long id);

    Page<EnquiryTableDTO> getAllActiveEnquiry(int page, int size, String sortBy, String sortDir, EnquiryFilter filter);

    Page<Enquiry> getAllEnquiry(int page, int size, String sortBy, String sortDir);

    Enquiry updateEnquiry(Enquiry updatedEnquiry, Long id);

    Enquiry createEnquiry(Enquiry newEnquiry);

    void deleteEnquiry(Long id);

    void closeEnquiry(Long id, String closeReason);

    void updateEnquiryStatus(Long id, EnquiryStatus status);

    /**
     * Records a human verdict on an AI-raised enquiry and clears its review flag.
     * Rejecting an enquiry marks it JUNK rather than deleting it -- a lead the agent got wrong is
     * the only evidence of what it gets wrong.
     */
    Enquiry applyAiReview(Long id, com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.AiReviewDecisionDTO decision);

    Enquiry getEnquiryByEnquiryNo(String enquiryNo);


    Long convertToQuotation(Long id);

    java.util.List<java.util.Map<String, Object>> getLinkedQuotations(Long enquiryId);

    // Conversation log -- the follow-up history, previously reachable only through the enquiry graph
    List<EnquiryConversationDTO> getConversations(Long enquiryId);

    /**
     * Logs a contact and moves lastContactedDate to match. Those two drifting apart is why
     * "when did we last chase this?" could not be answered: the column existed and was never
     * written to by anything.
     */
    EnquiryConversationDTO addConversation(Long enquiryId, EnquiryConversationDTO record);

    void deleteConversation(Long enquiryId, Long conversationId);

    // Bulk operations
    void bulkDelete(BulkDeleteRequest request);

    void bulkAssign(BulkAssignRequest request);
}
