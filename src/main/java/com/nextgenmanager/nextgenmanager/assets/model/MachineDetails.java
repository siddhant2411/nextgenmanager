package com.nextgenmanager.nextgenmanager.assets.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.nextgenmanager.nextgenmanager.production.model.WorkCenter;
import jakarta.persistence.*;
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
    private int id;

    @Column(nullable = false, unique = true, length = 50)
    private String machineCode;

    @Column(nullable = false, length = 100)
    private String machineName;

    private String description;

    // Work center linkage (critical)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_center_id")
    private WorkCenter workCenter;

    // Machine cost rate
    @Column(precision = 10, scale = 2)
    private BigDecimal costPerHour;

    // Capacity per day for scheduling
    @Column(precision = 10, scale = 2)
    private BigDecimal availableHoursPerDay;

    // Operational state
    @Enumerated(EnumType.ORDINAL)
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
