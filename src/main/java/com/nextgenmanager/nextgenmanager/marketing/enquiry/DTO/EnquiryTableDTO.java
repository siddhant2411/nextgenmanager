package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.math.BigDecimal;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryPriority;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnquiryTableDTO {

    private Long id;
    private String enqNo;
    private LocalDate enqDate;
    private String companyName;
    private LocalDate lastContactedDate;
    private Integer daysForNextFollowup;
    private LocalDate nextFollowupDate;
    private LocalDate closedDate;

    private EnquiryStatus status;
    private BigDecimal expectedRevenue;
    private String opportunityName;
    private String phone;
    private String email;

    private EnquiryPriority priority;
    private String assignedToName;
    private String city;
    private String state;
}
