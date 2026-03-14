package com.nextgenmanager.nextgenmanager.contact.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactPersonDetailDTO {
    private int id;
    private String personName;
    private String designation;
    private String department;
    private String emailId;
    private String phoneNumber;
    private String whatsappNumber;
    private boolean isPrimary;
}
