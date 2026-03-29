package com.nextgenmanager.nextgenmanager.contact.dto;

import com.nextgenmanager.nextgenmanager.contact.model.ContactType;
import com.nextgenmanager.nextgenmanager.contact.model.GstType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/** Create / update request for Contact. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestDTO {

    @NotBlank
    private String companyName;

    private String tradeName;

    @NotNull
    private ContactType contactType;

    // GST / Tax
    private String gstNumber;
    private GstType gstType = GstType.REGULAR;
    private String panNumber;

    // MSME
    private boolean msmeRegistered = false;
    private String msmeNumber;

    // Commercial
    private String defaultPaymentTerms;
    private Integer creditDays;
    private String currency = "INR";

    // Contact Info
    private String website;
    private String phone;
    private String email;
    private String notes;

    private List<ContactAddressDTO> addresses;
    private List<ContactPersonDetailDTO> personDetails;
}
