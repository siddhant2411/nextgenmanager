package com.nextgenmanager.nextgenmanager.component.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComponentAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer componentId;
    private String fieldChanged;      // what changed
    private String oldValue;          // previous value (optional)
    private String newValue;          // new value (optional)

    private Instant changedAt;

    private String changedBy;         // username or SYSTEM
    private String source;            // UI, API, SYSTEM
    private String comment;           // optional
    @Column(columnDefinition = "TEXT")
    private String payloadJson;       // optional JSON snapshot
}