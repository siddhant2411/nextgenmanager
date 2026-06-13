package com.nextgenmanager.nextgenmanager.common.approval.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "approvalpolicy")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ApprovalPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 40)
    private String documentType;

    @Column(length = 200)
    private String description;

    private boolean enabled = false;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, level ASC")
    private List<ApprovalRule> rules = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
