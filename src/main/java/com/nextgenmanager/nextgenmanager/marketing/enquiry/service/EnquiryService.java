package com.nextgenmanager.nextgenmanager.marketing.enquiry.service;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO.EnquiryTableDTO;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.Enquiry;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface EnquiryService {

    public Enquiry getEnquiry(int id);

    public Page<EnquiryTableDTO> getAllActiveEnquiry(int page, int size,String sortBy, String sortDir, String enqNo, String companyName, LocalDate lastContactedDate,
                                                     LocalDate enqDate, LocalDate closedDate, Integer daysForNetFollowUp,
                                                     String dateComparisonTypeLastContacted,
                                                     String dateComparisonTypeEnqDate,
                                                     String dateComparisonTypeClosedDate);

    public Page<Enquiry> getAllEnquiry(int page, int size,String sortBy, String sortDir);
    public Enquiry updateEnquiry(Enquiry updatedEnquiry,int id);

    public Enquiry createEnquiry(Enquiry newEnquiry);

    public void deleteEnquiry(int id);

    public void closeEnquiry(int id, String closeReason);

    public Enquiry getEnquiryByEnquiryNo(String enquiryNo);

    public Enquiry getEnquiryWithProductPrice(int id);

}
