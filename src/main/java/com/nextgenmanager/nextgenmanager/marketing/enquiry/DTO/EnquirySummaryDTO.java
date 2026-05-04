package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnquirySummaryDTO {
    private long totalLeads;
    private long newLeads;
    private long contacted;
    private long followUp;
    private long won;
    private long lost;
    private long overdueFollowups;
    private BigDecimal totalExpectedRevenue;
    private BigDecimal wonRevenue;
}
