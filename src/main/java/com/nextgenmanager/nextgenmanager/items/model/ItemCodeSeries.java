package com.nextgenmanager.nextgenmanager.items.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

/**
 * A named numbering series for auto item code generation.
 * Example: series "FBTM" with prefix "FBTM", separator "-", padding 4 → FBTM-0001, FBTM-0002, …
 */
@Entity
@Table(name = "itemCodeSeries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemCodeSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short unique key used to identify the series (e.g. "FBTM", "RM"). */
    @Column(unique = true, nullable = false, length = 20)
    private String seriesCode;

    /** Human-readable description (e.g. "Flush Bottom Valves"). */
    @Column(length = 255)
    private String description;

    /** Prefix prepended to every generated code (e.g. "FBTM"). */
    @Column(nullable = false, length = 20)
    private String prefix;

    /** Character(s) between prefix and number (default "-"). */
    @Column(nullable = false, length = 5)
    private String separator = "-";

    /** Zero-padding width for the sequence number (e.g. 4 → "0001"). */
    @Column(nullable = false)
    private int padding = 4;

    /** Last consumed sequence value; next code uses lastNumber + 1. */
    @Column(nullable = false)
    private int lastNumber = 0;

    @JsonProperty("isActive")
    @Column(nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    /** Returns the next code string WITHOUT incrementing lastNumber. Use for preview only. */
    public String previewNextCode() {
        return prefix + separator + String.format("%0" + padding + "d", lastNumber + 1);
    }

    /** Increments lastNumber and returns the newly generated code. Call within a @Transactional method. */
    public String consumeNextCode() {
        lastNumber++;
        return prefix + separator + String.format("%0" + padding + "d", lastNumber);
    }
}
