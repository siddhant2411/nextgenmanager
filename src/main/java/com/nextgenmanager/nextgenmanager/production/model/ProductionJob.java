package com.nextgenmanager.nextgenmanager.production.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "productionJob")
public class ProductionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(nullable = false, unique = true, length = 50)
    private String jobCode;

    @NotBlank
    @Column(nullable = false, length = 100, unique = true)
    private String jobName;

    @Column(precision = 10, scale = 2)
    private BigDecimal defaultSetupTime;

    @Column(precision = 10, scale = 2)
    private BigDecimal defaultRunTimePerUnit;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;
}
