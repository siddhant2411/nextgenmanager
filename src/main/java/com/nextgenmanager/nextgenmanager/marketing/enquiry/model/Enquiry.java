package com.nextgenmanager.nextgenmanager.marketing.enquiry.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import com.nextgenmanager.nextgenmanager.common.model.AppUser;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "enquiry")
public class Enquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(unique = true, nullable = false)
    private String enqNo;

    private String opportunityName;

    private LocalDate enqDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    private String manualCompanyName;

    private String contactPersonName;
    private String contactPersonPhone;
    private String contactPersonEmail;

    private LocalDate lastContactedDate;
    private int daysForNextFollowup;
    private LocalDate nextFollowupDate;
    private String followupRemarks;

    private String enquirySource;
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    private EnquiryStatus status = EnquiryStatus.NEW;

    @OneToMany(mappedBy = "enquiry", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<EnquiredProducts> enquiredProducts;

    @OneToMany(mappedBy = "enquiry", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<EnquiryConversationRecord> enquiryConversationRecords;

    @Column(precision = 15, scale = 2)
    private BigDecimal expectedRevenue = BigDecimal.ZERO;
    
    private Integer probability; // 0-100%
    private LocalDate targetCloseDate;

    private LocalDate closedDate;

    /** Verbatim text as recorded by sales — kept alongside the code, which is what reports count. */
    private String closeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "close_reason_id")
    private EnquiryCloseReason closeReasonCode;

    @Enumerated(EnumType.STRING)
    private EnquiryPriority priority = EnquiryPriority.WARM;

    @Enumerated(EnumType.STRING)
    private EnquiryType type = EnquiryType.PRODUCT;

    private String city;
    private String state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private AppUser assignedTo;

    private String leadQuality; // QUALIFIED, UNQUALIFIED, UNKNOWN

    @Column(columnDefinition = "TEXT")
    private String description;

    private Date deletedDate;

    // ── AI Lead Agent provenance (V160) ─────────────────────────────────────
    // Set only by the agent service account. Sales never types into these, and the update path
    // must not let a hand edit clear them -- provenance that a human can overwrite is not
    // provenance. Defaulted rather than nullable Boolean so the register never has to distinguish
    // "not AI" from "nobody said".

    /** True when this row was written by the AI Lead Agent rather than by a person. */
    private boolean aiGenerated = false;

    /** Agent's confidence in the extraction, 0.000–1.000. Null on hand-typed rows. */
    @Column(precision = 4, scale = 3)
    private BigDecimal aiConfidence;

    /** Deterministic qualification score, 0–100. Mirrors what drove the HOT/WARM/COLD priority. */
    private Integer aiScore;

    /** Model that produced the extraction, e.g. qwen3:4b. Kept so a bad batch is traceable to it. */
    @Column(length = 100)
    private String aiModel;

    /** Still awaiting a human decision on the review desk. */
    private boolean aiRequiresReview = false;

    /** Gmail message the enquiry came from. Unique where present — the agent's idempotency key. */
    @Column(length = 255)
    private String gmailMessageId;

    /** Gmail thread. Not unique: one chain can legitimately carry successive RFQs. */
    @Column(length = 255)
    private String gmailThreadId;

    private String createdBy;
    private String updatedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

}
