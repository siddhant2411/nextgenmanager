package com.nextgenmanager.nextgenmanager.production.model.workCenter;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "holidayCalendar")
public class HolidayCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;  // e.g. "India Plant 2026 Calendar"

    private String description;

    @OneToMany(mappedBy = "holidayCalendar", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Holiday> holidays = new HashSet<>();

    @OneToMany(mappedBy = "holidayCalendar", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CalendarOverride> overrides = new HashSet<>();

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "holidayCalendarWeeklyOffDays", joinColumns = @JoinColumn(name = "holidayCalendarId"))
    @Column(name = "dayOfWeek", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> weeklyOffDays;
}
