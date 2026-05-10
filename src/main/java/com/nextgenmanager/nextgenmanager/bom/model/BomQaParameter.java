package com.nextgenmanager.nextgenmanager.bom.model;

import com.nextgenmanager.nextgenmanager.bom.enums.QaParameterType;
import com.nextgenmanager.nextgenmanager.bom.model.routing.RoutingOperation;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "BomQaParameter")
public class BomQaParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routingOperationId", nullable = false)
    private RoutingOperation routingOperation;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QaParameterType parameterType;

    @Column(precision = 15, scale = 4)
    private BigDecimal minValue;

    @Column(precision = 15, scale = 4)
    private BigDecimal maxValue;

    private String unit;

    @Column(nullable = false)
    private Boolean critical = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
