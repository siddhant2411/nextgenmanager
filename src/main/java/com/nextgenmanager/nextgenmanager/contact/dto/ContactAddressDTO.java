package com.nextgenmanager.nextgenmanager.contact.dto;

import com.nextgenmanager.nextgenmanager.contact.model.AddressType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactAddressDTO {
    private int id;
    private AddressType addressType;
    private boolean isDefault;
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String pinCode;
    private String country;
}
