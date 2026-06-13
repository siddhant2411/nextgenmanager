package com.nextgenmanager.nextgenmanager.common.approval.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Table(name = "approvalstep")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    private int level;

    @Column(nullable = false, length = 100)
    private String approverRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StepStatus status = StepStatus.PENDING;

    @Column(length = 100)
    private String actedBy;

    private Date actedAt;

    @Column(length = 500)
    private String comment;
}
