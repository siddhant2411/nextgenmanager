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
@Table(name = "document_branding")
public class DocumentBranding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BrandingMode mode = BrandingMode.NONE;

    /** Stored already scaled down and re-encoded — never the bytes the user uploaded. */
    @Column(columnDefinition = "bytea")
    private byte[] logoImage;

    @Column(length = 30)
    private String logoContentType;

    private Integer logoWidthPx;

    private Integer logoHeightPx;

    @Column(columnDefinition = "bytea")
    private byte[] letterheadImage;

    @Column(length = 30)
    private String letterheadContentType;

    private Integer letterheadWidthPx;

    private Integer letterheadHeightPx;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;
}
