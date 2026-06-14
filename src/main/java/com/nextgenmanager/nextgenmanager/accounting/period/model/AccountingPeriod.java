package com.nextgenmanager.nextgenmanager.accounting.period.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "accountingperiod")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AccountingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financialYear_id", nullable = false)
    private FinancialYear financialYear;

    @Column(nullable = false)
    private int periodNumber;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PeriodStatus status = PeriodStatus.OPEN;
}
