package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkAssignRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.BulkDeleteRequest;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface EnquiryService {

    Enquiry getEnquiry(Long id);

    Page<EnquiryTableDTO> getAllActiveEnquiry(int page, int size, String sortBy, String sortDir, String enqNo,
                                              String companyName, LocalDate lastContactedDate,
                                              LocalDate enqDate, LocalDate closedDate, Integer daysForNetFollowUp,
                                              String dateComparisonTypeLastContacted,
                                              String dateComparisonTypeEnqDate,
                                              String dateComparisonTypeClosedDate);

    Page<Enquiry> getAllEnquiry(int page, int size, String sortBy, String sortDir);

    Enquiry updateEnquiry(Enquiry updatedEnquiry, Long id);

    Enquiry createEnquiry(Enquiry newEnquiry);

    void deleteEnquiry(Long id);

    void closeEnquiry(Long id, String closeReason);

    void updateEnquiryStatus(Long id, EnquiryStatus status);

    Enquiry getEnquiryByEnquiryNo(String enquiryNo);

    com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquirySummaryDTO getEnquirySummary();

    Long convertToQuotation(Long id);

    java.util.List<java.util.Map<String, Object>> getLinkedQuotations(Long enquiryId);

    // Bulk operations
    void bulkDelete(BulkDeleteRequest request);

    void bulkAssign(BulkAssignRequest request);
}
