package com.nextgenmanager.nextgenmanager.contact.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isPrimary")
    private boolean isPrimary;
}
