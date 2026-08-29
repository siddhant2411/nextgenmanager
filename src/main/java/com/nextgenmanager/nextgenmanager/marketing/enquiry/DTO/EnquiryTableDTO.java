package com.nextgenmanager.nextgenmanager.marketing.enquiry.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.math.BigDecimal;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryCloseOutcome;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryStatus;
import com.nextgenmanager.nextgenmanager.marketing.enquiry.model.EnquiryPriority;

/**
 * One row of the enquiry list.
 *
 * The fields below the divider exist so that a question about the register can be answered from
 * the list itself. Without them, working out why enquiries were closed, which came from which
 * source, or whether anyone chased them meant fetching every enquiry individually: a single
 * analysis of 329 enquiries cost 338 API calls, 329 of them pure waste.
 *
 * Built with a builder rather than a 29-argument constructor -- positional construction at that
 * width is how a city ends up in the state column.
 */
@Data
@Builder
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

    // ---------------------------------------------------------------- added to kill the N+1

    /** Null when the enquiry carries only a free-text company name -- no contact to drill into. */
    private Long contactId;
    private Long assignedToId;
    private String enquirySource;

    /** Reportable close reason. closeReasonText is the sentence sales actually wrote. */
    private String closeReasonCode;
    private EnquiryCloseOutcome closeOutcome;
    private String closeReasonText;

    /** Item codes (or free-text names) joined with commas, so a multi-item enquiry stays one row. */
    private String productSummary;
    private Integer productCount;

    private Integer quotationCount;
    private Integer probability;

    /** How many follow-ups were logged, and when the last one happened. Zero means never chased. */
    private Integer conversationCount;
    private LocalDate lastConversationDate;

    /**
     * Orders actually booked against this enquiry, and their taxable value. This is the only
     * field on the row that reports money that exists -- expectedRevenue is an estimate typed in
     * when the enquiry was raised.
     */
    private Integer salesOrderCount;
    private BigDecimal bookedAmount;

    // ------------------------------------------------------- AI Lead Agent provenance (V160)

    /**
     * Written by the AI Lead Agent rather than by a person, and how sure it was.
     *
     * On the row because the distinction has to survive into any analysis of the register. A
     * close rate that silently mixes machine-extracted leads with ones a salesperson qualified
     * on a call is a number with no defensible meaning, and re-fetching 300 enquiries to find
     * out which is which is the N+1 this DTO exists to kill.
     */
    private Boolean aiGenerated;
    private BigDecimal aiConfidence;

    /** Still waiting on a human. The review desk filters on exactly this. */
    private Boolean aiRequiresReview;

    /**
     * Gmail thread the enquiry came from. Here so the agent can dedupe an inbound reply against
     * the register in one call, instead of opening every candidate enquiry to look.
     */
    private String gmailThreadId;
}
