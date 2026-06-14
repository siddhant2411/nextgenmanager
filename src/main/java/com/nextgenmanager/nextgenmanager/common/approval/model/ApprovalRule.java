package com.nextgenmanager.nextgenmanager.common.approval.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "approvalrule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private ApprovalPolicy policy;

    private int sortOrder = 0;

    /**
     * JSON array of conditions, e.g.:
     * [{"attribute":"amount","operator":">=","value":"50000"}]
     * Operators: >=, <=, =, !=, IN, NOT_IN
     * IN/NOT_IN value is comma-separated: "EXPORT_WITH_LUT,SEZ"
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String conditions = "[]";

    @Column(nullable = false, length = 100)
    private String approverRole;

    @Column(nullable = false)
    private int level = 1;

    @Column(length = 200)
    private String description;

    private Date deletedDate;
}
