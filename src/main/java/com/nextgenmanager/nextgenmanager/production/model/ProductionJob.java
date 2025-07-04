package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
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

    @NotBlank
    @Column(nullable = false, length = 100, unique = true)
    private String jobName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_details_id", referencedColumnName = "id")
    private MachineDetails machineDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobRole roleRequired;

    @DecimalMin(value = "0.0", inclusive = false)
    @Column(precision = 10, scale = 2)
    private BigDecimal costPerHour;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    public enum JobRole {
        OPERATOR, SUPERVISOR, MAINTENANCE, QUALITY_CONTROL
    }
}

