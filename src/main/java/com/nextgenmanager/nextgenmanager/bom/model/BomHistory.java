package com.nextgenmanager.nextgenmanager.bom.model;

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
public class BomHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer bomId;

    private Integer versionNumber;

    private String revision;

    private Instant changedAt;

    private String changedBy;

    private String changeType;  // CREATED, UPDATED, STRUCTURE_CHANGE

    @Column(columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(length = 2000)
    private String changeSummary;
}
