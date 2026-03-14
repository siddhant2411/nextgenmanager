package com.nextgenmanager.nextgenmanager.contact.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "contact_person_detail")
public class ContactPersonDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    private String personName;

    private String designation;

    private String department;

    @Email
    private String emailId;

    @Size(min = 8, max = 15)
    private String phoneNumber;

    @Column(length = 15)
    private String whatsappNumber;

    /** Primary contact person for this company — used as default in communications. */
    @Column(nullable = false)
    private boolean isPrimary = false;

    @ManyToOne
    @JoinColumn(name = "contact_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private Contact contact;
}

