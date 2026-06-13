package com.nextgenmanager.nextgenmanager.accounting.period.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.util.Date;

@Entity
@Table(name = "financialyear")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FinancialYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /** The cutover date for mid-year go-live. Null = fresh start from beginning of FY. */
    private LocalDate openingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FyStatus status = FyStatus.ACTIVE;

    /** FK set after the OPENING voucher is posted. */
    @Column(name = "openingEntryVoucher_id")
    private Long openingEntryVoucherId;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;
}
