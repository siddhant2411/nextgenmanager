package com.nextgenmanager.nextgenmanager.production.model.workCenter;

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
import java.util.List;

@Entity
@Table(name = "workCenter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkCenter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true, length = 50)
    private String centerCode;   // e.g. WC-CUT, WC-WELD

    @Column(nullable = false, length = 100)
    private String centerName;   // e.g. Cutting Section

    @Column(length = 500)
    private String description;

    // Machine hourly rate at this center (labor cost comes from LaborRole)
    @Column(precision = 10, scale = 2)
    private BigDecimal machineCostPerHour;

    // Overhead percentage applied on top of (machine + labor) cost
    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal overheadPercentage = BigDecimal.ZERO;

    // How many hours per day this center normally operates
    @Column(precision = 10, scale = 2)
    private BigDecimal availableHoursPerDay;

    // Status helpful for scheduling
    @Enumerated(EnumType.ORDINAL)
    private WorkCenterStatus workCenterStatus = WorkCenterStatus.ACTIVE;

    @CreationTimestamp
    @Column(updatable = false)
    private Date creationDate;

    @UpdateTimestamp
    private Date updatedDate;

    private Date deletedDate;

    private String department;

    private String location;

    private Integer maxLoadPercentage;

    private String supervisor;

    @ElementCollection
    @CollectionTable(
            name = "workCenterAvailableShifts",
            joinColumns = @JoinColumn(name = "workCenterId")
    )
    private List<String> availableShifts;

    @OneToMany(mappedBy = "workCenter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkCenterShift> shifts;

    @OneToMany(mappedBy = "workCenter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MachineDetails> workStations;

    /***
     * How Scheduling Logic Should Work
        When checking availability for a date:
        Get work center shifts for that weekday
        Check if date exists in holiday table
        If fullDay → capacity = 0
        If partial → reduce shift duration
        Else → normal shift duration
     ***/
    @ManyToOne
    @JoinColumn(name = "holidayCalendarId")
    private HolidayCalendar holidayCalendar;

    public enum WorkCenterStatus {
        ACTIVE,
        UNDER_MAINTENANCE,
        SHUTDOWN,
        OVERLOADED
    }
}



