package com.nextgenmanager.nextgenmanager.contact.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "contact_address")
public class ContactAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AddressType addressType = AddressType.BILLING;

    @Column(nullable = false)
    private boolean isDefault = false;

    private String street1;
    private String street2;
    private String city;
    private String state;
    private String pinCode;
    private String country = "India";

    @ManyToOne
    @JoinColumn(name = "contact_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private Contact contact;
}
