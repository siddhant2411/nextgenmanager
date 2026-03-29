package com.nextgenmanager.nextgenmanager.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fileAttachment")
public class FileAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Generic linkage fields
    @Column(nullable = false)
    private String referenceType; // e.g., "InventoryItem", "Quotation", "BOM"

    @Column(nullable = false)
    private Long referenceId; // e.g., the ID of the inventory item or quotation

    // File storage info
    @Column(nullable = false)
    private String fileName; // stored name in MinIO (XXX_date.pdf)

    @Column(nullable = false)
    private String originalName; // original uploaded name

    private String folder; // logical folder in MinIO, e.g. "inventory", "quotation"

    private String contentType;

    private Long size;

    private String uploadedBy;

    private Date uploadedAt;

    private Date deletedDate;

    private Date presignedUrlCreationDate;

    private String presignedUrl;

    // Optional description (e.g., “Technical Drawing”, “Quotation PDF”)
    private String description;
}
