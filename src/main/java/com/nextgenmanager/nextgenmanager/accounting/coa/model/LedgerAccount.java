package com.nextgenmanager.nextgenmanager.accounting.coa.model;

import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "ledgeraccount")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private AccountGroup group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AccountNature nature;

    @Column(precision = 12, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(length = 2)
    private String openingBalanceDrCr;

    private boolean isControlAccount = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SubLedgerType subLedgerType = SubLedgerType.NONE;

    // Populated for party sub-ledgers (one LedgerAccount per customer/vendor Contact)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    private boolean gstApplicable = false;

    @Column(precision = 5, scale = 2)
    private BigDecimal gstRate;

    private boolean isBankAccount = false;
    private boolean isCashAccount = false;
    private boolean isReconcilable = false;

    /** Seeded structural account wired into auto-posting — cannot be deleted. */
    private boolean isSystem = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
