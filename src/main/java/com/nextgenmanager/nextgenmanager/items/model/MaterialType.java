package com.nextgenmanager.nextgenmanager.items.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

/** Dynamic lookup table for the basicMaterial field on ProductSpecification. */
@Entity
@Table(name = "materialType")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MaterialType {

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
