package com.nextgenmanager.nextgenmanager.contact.dto;

import com.nextgenmanager.nextgenmanager.contact.model.ContactType;
import com.nextgenmanager.nextgenmanager.contact.model.GstType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for dropdowns and search results.
 * Used in vendor selection, customer lookup, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSummaryDTO {
    private int id;
    private String contactCode;
    private String companyName;
    private String tradeName;
    private ContactType contactType;
    private String gstNumber;
    private GstType gstType;
    private boolean msmeRegistered;
    private String phone;
    private String email;
    /** Primary contact person name — shown in dropdowns. */
    private String primaryContactName;
    /** Billing city/state for display. */
    private String location;
}
