package com.nextgenmanager.nextgenmanager.marketing.enquiry.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Entity
@Data
@Table(name = "enquiryConversationRecord")
public class EnquiryConversationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    private String conversation;

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
