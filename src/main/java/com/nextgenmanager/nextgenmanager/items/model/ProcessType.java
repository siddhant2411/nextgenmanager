package com.nextgenmanager.nextgenmanager.items.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

/**
 * Dynamic lookup table for the processType field on ProductSpecification.
 * Describes the primary manufacturing/fabrication process for an item
 * (e.g. CNC Machining, Welding, Casting, Forging, Bought Out).
 * Used to guide routing template selection and work-centre assignments.
 */
@Entity
@Table(name = "processType")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    @JsonProperty("isActive")
    @Column(nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;
}
