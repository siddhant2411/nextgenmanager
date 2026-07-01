package com.nextgenmanager.nextgenmanager.company.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "company_details")
public class CompanyDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(length = 200)
    private String legalName;

    @Column(length = 200)
    private String tradeName;

    @Column(length = 15)
    private String gstNumber;

    @Column(length = 10)
    private String panNumber;

    /** Tax Deduction & Collection Account Number — deductor identifier on the 26Q/27Q return. */
    @Column(length = 15)
    private String tan;

    @Column(length = 21)
    private String cinNumber;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 200)
    private String website;

    @Column(length = 200)
    private String street1;

    @Column(length = 200)
    private String street2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 10)
    private String pinCode;

    /** 2-digit GST state code (e.g. "27" for Maharashtra). Stored for GST treatment derivation; not shown on UI. */
    @Column(length = 2)
    private String stateCode;

    @Column(length = 100)
    private String country = "India";

    @Column(length = 3)
    private String currency = "INR";

    private Integer financialYearStartMonth = 4;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 200)
    private String bankName;

    @Column(length = 30)
    private String bankAccountNumber;

    @Column(length = 20)
    private String bankIfscCode;

    @Column(length = 200)
    private String bankBranch;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;
}
