package com.nextgenmanager.nextgenmanager.accounting.coa.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;

@Entity
@Table(name = "accountgroup")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AccountGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentGroup_id")
    private AccountGroup parentGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AccountNature nature;

    @Column(length = 120)
    private String scheduleIIIHead;

    private int sortOrder;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
