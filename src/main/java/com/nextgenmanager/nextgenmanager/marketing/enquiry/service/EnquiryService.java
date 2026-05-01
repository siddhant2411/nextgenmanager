package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface EnquiryService {

    public Enquiry getEnquiry(Long id);

    public Page<EnquiryTableDTO> getAllActiveEnquiry(int page, int size,String sortBy, String sortDir, String enqNo, String companyName, LocalDate lastContactedDate,
                                                     LocalDate enqDate, LocalDate closedDate, Integer daysForNetFollowUp,
                                                     String dateComparisonTypeLastContacted,
                                                     String dateComparisonTypeEnqDate,
                                                     String dateComparisonTypeClosedDate);

    public Page<Enquiry> getAllEnquiry(int page, int size,String sortBy, String sortDir);
    public Enquiry updateEnquiry(Enquiry updatedEnquiry, Long id);

    public Enquiry createEnquiry(Enquiry newEnquiry);

    public void deleteEnquiry(Long id);

    public void closeEnquiry(Long id, String closeReason);

    public void updateEnquiryStatus(Long id, com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus status);

    public Enquiry getEnquiryByEnquiryNo(String enquiryNo);

//    public Enquiry getEnquiryWithProductPrice(int id);

}
