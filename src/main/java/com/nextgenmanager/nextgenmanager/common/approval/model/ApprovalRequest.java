package com.nextgenmanager.nextgenmanager.common.approval.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "approvalrequest")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String documentType;

    @Column(nullable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(length = 100)
    private String requestedBy;

    @CreationTimestamp
    private Date requestedAt;

    private Date resolvedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("level ASC")
    private List<ApprovalStep> steps = new ArrayList<>();
}
