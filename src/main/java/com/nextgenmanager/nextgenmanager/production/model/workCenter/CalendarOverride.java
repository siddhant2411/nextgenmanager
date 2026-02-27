package com.nextgenmanager.nextgenmanager.production.model.workCenter;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "calendarOverride",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_calendar_override",
                columnNames = {"holidayCalendarId", "overrideDate"}
        ))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class CalendarOverride {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "holidayCalendarId", nullable = false)
    private HolidayCalendar holidayCalendar;

    @Column(name = "overrideDate", nullable = false)
    private LocalDate overrideDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OverrideType overrideType; // WORKING, OFF

    @Column(length = 300)
    private String reason;

     public enum OverrideType {
        WORKING, OFF
    }
}
