package com.nextgenmanager.nextgenmanager.items.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ItemCodeMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category; // e.g., PRODUCT_TYPE, MODEL_CODE, GROUP_CODE

    @Column(nullable = false)
    private String keyword; // e.g., "valve", "flush bottom"

    @Column(nullable = false)
    private String code; // e.g., "VAL", "FBV"
}
