package com.nextgenmanager.nextgenmanager.marketing.enquiry.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.util.Date;

@Entity
@Data
@Table(name = "enquiryConversationRecord")
public class EnquiryConversationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String conversation;

    /**
     * When the contact actually happened, which is not the same as when the row was written.
     * Left null for notes typed as they occur; readers fall back to creationDate.
     */
    private LocalDate conversationDate;

    @Enumerated(EnumType.STRING)
    private ConversationType conversationType = ConversationType.NOTE;

    public enum ConversationType {
        CALL, EMAIL, MEETING, NOTE
    }

    @ManyToOne
    @JoinColumn(name = "enquiry_conversation_id", referencedColumnName = "id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private Enquiry enquiry;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

}
