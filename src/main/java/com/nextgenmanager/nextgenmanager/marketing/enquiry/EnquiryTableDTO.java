package com.nextgenmanager.nextgenmanager.marketing.enquiry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnquiryTableDTO {

    private int id;
    private String enqNo;
    private LocalDate enqDate;
    private String companyName;
    private LocalDate lastContactedDate;
    private Integer daysForNextFollowup;
    private LocalDate closedDate;

}
