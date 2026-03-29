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

    private LocalDate closedDate;
    private String closeReason;
    private Date deletedDate;

    private String createdBy;
    private String updatedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    @PrePersist
    public void prePersist() {
        if (this.enqNo == null || this.enqNo.isBlank()) {
            this.enqNo = generateShortUUID();
        }
    }

    private static String generateShortUUID() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
