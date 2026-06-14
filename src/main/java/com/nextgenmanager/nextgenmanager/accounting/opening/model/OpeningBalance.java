package com.nextgenmanager.nextgenmanager.accounting.opening.model;

import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.contact.model.Contact;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Entity
@Table(name = "openingbalance")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class OpeningBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledgerAccount_id", nullable = false)
    private LedgerAccount ledgerAccount;

    /** Populated for party sub-ledger opening balances (individual customer/vendor balances). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 2)
    private String drCr;

    @Column(nullable = false)
    private LocalDate openingDate;

    @CreationTimestamp
    private Date importedAt;

    private Date deletedDate;
}
