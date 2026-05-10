package com.nextgenmanager.nextgenmanager.common.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Entity
@Table(name = "documentchecklistitem")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DocumentChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "PURCHASE_ORDER", "VENDOR_BILL" — matches the caller's domain concept. */
    @Column(nullable = false, length = 50)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentType documentType;

    /** Only set when documentType == CUSTOM; shown as the display label. */
    @Column(length = 255)
    private String customLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChecklistStatus status = ChecklistStatus.PENDING;

    /** Optional FK to the uploaded file. */
    private Long fileAttachmentId;

    /** Cached original filename for quick display without JOIN. */
    @Column(length = 255)
    private String fileName;

    /** Cached presigned URL — refreshed on read. */
    @Column(length = 2048)
    private String fileUrl;

    private Date receivedDate;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(updatable = false)
    private Date createdDate;

    @UpdateTimestamp
    private Date updatedDate;

    public enum ChecklistStatus { PENDING, RECEIVED, NOT_APPLICABLE }
}
