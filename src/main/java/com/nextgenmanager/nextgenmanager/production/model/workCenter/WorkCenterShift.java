package com.nextgenmanager.nextgenmanager.production.model.workCenter;


import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Date;
import java.util.Set;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(
        name = "workCenterShift",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_wc_shift_name", columnNames = {"workCenterId", "shiftName"}),
                @UniqueConstraint(name = "uk_wc_shift_code", columnNames = {"workCenterId", "shiftCode"})
        },
        indexes = {
                @Index(name = "idx_wc_shift_work_center", columnList = "workCenterId")
        }

)
public class WorkCenterShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workCenterId", nullable = false)
    private WorkCenter workCenter;

    @NotBlank
    @Column(name = "shiftName", nullable = false, length = 50)
    private String shiftName; // Morning, Afternoon, Night

    @NotNull
    @Column(name = "startTime", nullable = false)
    private LocalTime startTime;

    @NotNull
    @Column(name = "endTime", nullable = false)
    private LocalTime endTime;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "workCenterShiftDays",
            joinColumns = @JoinColumn(name = "shiftId"),
            uniqueConstraints = @UniqueConstraint(
            name = "uk_shift_day",
            columnNames = {"shiftId", "dayOfWeek"}
    ))
    @Enumerated(EnumType.STRING)
    @Column(name = "dayOfWeek", nullable = false)
    @NotNull
    private Set<DayOfWeek> activeDays;

    @Column(name = "breakMinutes", nullable = false)
    @Min(0)
    private Integer breakMinutes = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "allowShiftOverlap", nullable = false)
    private boolean allowShiftOverlap = false;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    @AssertTrue(message = "End time must not be equal to start time")
    public boolean isValidShiftTime() {
        return !startTime.equals(endTime);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkCenterShift)) return false;
        return id != null && id.equals(((WorkCenterShift) o).id);
    }

    @Override
    public int hashCode() {
        return 31;
    }

    @NotBlank
    @Column(name = "shiftCode", nullable = false, length = 20)
    private String shiftCode;

    @NotNull
    @Min(0)
    @Column(name = "plannedCapacityMinutes", nullable = false)
    private Integer plannedCapacityMinutes;

}
