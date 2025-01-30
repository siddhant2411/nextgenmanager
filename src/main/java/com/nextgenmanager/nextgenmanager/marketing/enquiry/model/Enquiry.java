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
    private int id;

    @Column(unique = true, nullable = false)
    private String enqNo=generateShortUUID();

    private LocalDate enqDate;

    @ManyToOne(fetch = FetchType.LAZY) // Lazy load the associated Contact
    @JoinColumn(name = "contact_id", referencedColumnName = "id") // Foreign key mapping
    private Contact contact;

    private LocalDate lastContactedDate;

    private int daysForNextFollowup;

    private String enquireTrough;

    @OneToMany(mappedBy = "enquiry", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<EnquiredProducts> enquiredProducts;

    @OneToMany(mappedBy = "enquiry", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<EnquiryConversationRecord> enquiryConversationRecords;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date closedDate;

    private String closeReason;

    private Date deletedDate;



    private static String generateShortUUID() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
