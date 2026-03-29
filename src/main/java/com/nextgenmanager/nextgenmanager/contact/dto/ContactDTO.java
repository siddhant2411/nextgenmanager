package com.nextgenmanager.nextgenmanager.contact.dto;

import com.nextgenmanager.nextgenmanager.contact.model.ContactType;
import com.nextgenmanager.nextgenmanager.contact.model.GstType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/** Full contact response DTO. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDTO {

    private int id;
    private String contactCode;
    private String companyName;
    private String tradeName;
    private ContactType contactType;

    // GST / Tax
    private String gstNumber;
    private GstType gstType;
    private String panNumber;

    // MSME
    private boolean msmeRegistered;
    private String msmeNumber;

    // Commercial
    private String defaultPaymentTerms;
    private Integer creditDays;
    private String currency;

    // Contact Info
    private String website;
    private String phone;
    private String email;
    private String notes;

    private List<ContactAddressDTO> addresses;
    private List<ContactPersonDetailDTO> personDetails;

    private Date creationDate;
    private Date updatedDate;
}
