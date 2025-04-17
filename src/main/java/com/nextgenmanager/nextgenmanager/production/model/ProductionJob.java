package com.nextgenmanager.nextgenmanager.production.model;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
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
@Table(name = "productionJob")
public class ProductionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(nullable = false)
    private String jobName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_details_id", referencedColumnName = "id")
    private MachineDetails machineDetails;

    private String roleRequired;

    @Column(precision = 10, scale = 2)
    private BigDecimal costPerHour;

    private String description;


    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

}
