package com.nextgenmanager.nextgenmanager.assets.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nextgenmanager.nextgenmanager.production.model.workCenter.WorkCenter;
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
@Table(name = "MachineDetails")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MachineDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 50)
    private String machineCode;

    @Column(nullable = false, length = 100)
    @NotBlank
    private String machineName;

    private String description;

    // Work center linkage (critical)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workCenterId", nullable = false)
    private WorkCenter workCenter;

    // Machine cost rate
    @DecimalMin("0.0")
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal costPerHour = BigDecimal.ZERO;


    // Operational state
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MachineStatus machineStatus = MachineStatus.ACTIVE;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    public enum MachineStatus {
        ACTIVE,
        UNDER_MAINTENANCE,
        BREAKDOWN,
        OUT_OF_SERVICE
    }
}
