package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnquiryCloseReasonDTO {
    private Long id;
    private String code;
    private String description;
    private EnquiryCloseOutcome outcome;
    private Integer displayOrder;
    private Boolean isActive;
}
