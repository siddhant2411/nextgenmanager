package com.nextgenmanager.nextgenmanager.contact.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDashboardDTO {
    private long totalContacts;
    private long customers;
    private long vendors;
    private long bothType;
    private long msmeRegistered;
    private long gstRegistered;
    private long recentlyAdded; // Last 30 days
}
