package com.nextgenmanager.nextgenmanager.assets.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "machineProductionLog",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mpl_machine_date_shift",
                        columnNames = {"machineId", "productionDate", "shiftId"}
                )
        },
        indexes = {
                @Index(name = "idx_mpl_machine_date", columnList = "machineId, productionDate")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MachineProductionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machineId", nullable = false)
    private MachineDetails machine;

    @Column(name = "productionDate", nullable = false)
    private LocalDate productionDate;

    @Column(name = "shiftId")
    private Long shiftId;

    @Column(name = "plannedQuantity")
    @Min(0)
    private Integer plannedQuantity;

    @Column(name = "actualQuantity")
    @Min(0)
    private Integer actualQuantity;

    @Column(name = "rejectedQuantity")
    @Min(0)
    private Integer rejectedQuantity;

    @Column(name = "runtimeMinutes")
    private Integer runtimeMinutes;

    @Column(name = "downtimeMinutes")
    private Integer downtimeMinutes;
}
