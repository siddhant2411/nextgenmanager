package com.nextgenmanager.nextgenmanager.production.model.workCenter;


import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "holiday",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_calendar_date",
                columnNames = {"holidayCalendarId", "holidayDate"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "holidayCalendarId", nullable = false)
    private HolidayCalendar holidayCalendar;

    @Column(nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private boolean fullDay = true;

    // Partial-day holiday time window
    private LocalTime startTime;
    private LocalTime endTime;

    // If fullDay=true then times must be null.
    // If fullDay=false then both times are required and endTime must be after startTime.
    @AssertTrue(message = "Invalid partial holiday time window")
    public boolean isValidPartialWindow() {
        if (fullDay) {
            return startTime == null && endTime == null;
        }
        return startTime != null && endTime != null && endTime.isAfter(startTime);
    }
}
