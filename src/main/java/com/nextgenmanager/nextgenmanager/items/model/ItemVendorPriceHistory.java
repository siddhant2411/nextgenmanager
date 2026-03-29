package com.nextgenmanager.nextgenmanager.items.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "itemVendorPriceHistory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemVendorPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itemVendorPriceId", nullable = false)
    private ItemVendorPrice itemVendorPrice;

    @Column(precision = 14, scale = 4)
    private BigDecimal oldPrice;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal newPrice;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Date changedDate;

    /** Username of who changed the price — populated from security context. */
    @Column(length = 100)
    private String changedBy;

    private String remarks;
}
