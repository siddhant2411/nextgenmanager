package com.nextgenmanager.nextgenmanager.packaging.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * One physical box on a packing slip.
 *
 * <p>Named {@code PackageBox} rather than {@code Package} — {@code java.lang.Package} already owns
 * that name, and shadowing a JDK class buys nothing.
 */
@Entity
@Table(name = "packagebox")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PackageBox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "packingslip_id", nullable = false)
    private PackingSlip packingSlip;

    /** Sequential within its slip — 1, 2, 3 — assigned by the service, not a global number. */
    @Column(nullable = false)
    private Integer boxNumber;

    @Column(length = 50) private String boxType;

    @Column(precision = 18, scale = 4) private BigDecimal lengthCm;
    @Column(precision = 18, scale = 4) private BigDecimal widthCm;
    @Column(precision = 18, scale = 4) private BigDecimal heightCm;
    @Column(precision = 18, scale = 4) private BigDecimal grossWeightKg;
    @Column(precision = 18, scale = 4) private BigDecimal netWeightKg;

    @Column(length = 500) private String shippingMarks;

    @OneToMany(mappedBy = "packageBox", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PackageLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP) private Date creationDate;
    @Temporal(TemporalType.TIMESTAMP) private Date updatedDate;
    @Temporal(TemporalType.TIMESTAMP) private Date deletedDate;
}
