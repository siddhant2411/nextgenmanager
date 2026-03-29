package com.nextgenmanager.nextgenmanager.contact.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "contact",
    indexes = {
        @Index(name = "idx_contact_code", columnList = "contactCode", unique = true),
        @Index(name = "idx_contact_type", columnList = "contactType"),
        @Index(name = "idx_contact_gst", columnList = "gstNumber")
    }
)
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    /** Auto-generated unique code: V-001 (vendor), C-001 (customer), B-001 (both). */
    @Column(unique = true, length = 20)
    private String contactCode;

    /** Legal / registered company name. */
    @Column(nullable = false)
    private String companyName;

    /** Trade name if different from company name (common in Indian SMEs). */
    private String tradeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactType contactType;

    // ── GST / Tax Info ──────────────────────────────────────────────────────

    /** 15-digit GSTIN. Unique per state registration. */
    @Column(length = 15)
    private String gstNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GstType gstType = GstType.REGULAR;

    /** 10-digit PAN — required for TDS deduction above threshold. */
    @Column(length = 10)
    private String panNumber;

    // ── MSME / Udyam ────────────────────────────────────────────────────────

    /** Whether registered under MSME / Udyam scheme. */
    @Column(nullable = false)
    private boolean msmeRegistered = false;

    /** Udyam Registration Number (URN) — format: UDYAM-XX-00-0000000. */
    @Column(length = 30)
    private String msmeNumber;

    // ── Commercial Terms ────────────────────────────────────────────────────

    /** Default payment terms, e.g. "30 days net", "Advance", "LC 60 days". */
    @Column(length = 100)
    private String defaultPaymentTerms;

    /** Credit period in days for this contact. */
    private Integer creditDays;

    /** Default transaction currency. */
    @Column(length = 3)
    private String currency = "INR";

    // ── Contact Info ────────────────────────────────────────────────────────

    private String website;

    /** Primary phone / landline. */
    @Column(length = 20)
    private String phone;

    /** Primary email for invoices / PO communication. */
    private String email;

    private String notes;

    // ── Relationships ───────────────────────────────────────────────────────

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ContactAddress> addresses;

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ContactPersonDetail> personDetails;

    // ── Audit ───────────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
