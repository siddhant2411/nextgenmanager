package com.nextgenmanager.nextgenmanager.marketing.template.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "message_template")
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    private String subject; // For email templates

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateChannel channel = TemplateChannel.WHATSAPP;

    @Enumerated(EnumType.STRING)
    private TemplateCategory category = TemplateCategory.ENQUIRY;

    @Column(name = "is_default")
    private boolean isDefault = false;

    @Column(name = "deleted_date")
    private Date deletedDate;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "creation_date", updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    @Column(name = "updated_date")
    private Date updatedDate;
}
