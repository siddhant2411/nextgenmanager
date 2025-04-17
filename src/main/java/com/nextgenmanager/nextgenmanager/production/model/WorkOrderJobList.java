package com.nextgenmanager.nextgenmanager.production.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "workOrderJobList")
public class WorkOrderJobList {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_job", referencedColumnName = "id",nullable = false)
    private ProductionJob productionJob;

    @Column(precision = 10, scale = 1)
    private BigDecimal numberOfHours;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workOrderProductionTemplate_job_list_id", referencedColumnName = "id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @JsonBackReference
    private WorkOrderProductionTemplate workOrderProductionTemplate;

}
