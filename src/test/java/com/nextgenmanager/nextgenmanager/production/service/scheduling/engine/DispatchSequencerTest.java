package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchSequencerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 8, 0);
    private static final int URGENT = 1;
    private static final int NORMAL = 3;
    private static final int LOW = 4;

    /** A job due {@code days} working days out, at 480 productive minutes a day. */
    private static DispatchSequencer.Job<String> job(String name, int rank, Integer days, double work) {
        if (days == null) return new DispatchSequencer.Job<>(name, rank, null, work, 0);
        return new DispatchSequencer.Job<>(name, rank, NOW.plusDays(days), work, days * 480.0);
    }

    private static List<String> order(DispatchRule rule, List<DispatchSequencer.Job<String>> jobs) {
        return DispatchSequencer.sequence(jobs, rule).stream().map(DispatchSequencer.Job::ref).toList();
    }

    @Test
    void priorityPutsUrgentFirstWhateverItsDueDate() {
        List<DispatchSequencer.Job<String>> jobs = List.of(
                job("normal-soon", NORMAL, 1, 60),
                job("urgent-later", URGENT, 10, 60),
                job("normal-later", NORMAL, 5, 60));

        assertThat(order(DispatchRule.PRIORITY, jobs)).containsExactly("urgent-later", "normal-soon", "normal-later");
    }

    @Test
    void eddPutsTheEarliestDueFirstAndUndatedLast() {
        List<DispatchSequencer.Job<String>> jobs = List.of(
                job("undated", URGENT, null, 60),
                job("later", URGENT, 10, 60),
                job("soon", LOW, 1, 60));

        assertThat(order(DispatchRule.EDD, jobs)).containsExactly("soon", "later", "undated");
    }

    @Test
    void criticalRatioFavoursTheOrderWithLeastTimeForItsWork() {
        // Due later but has far more work: ratio 2400/2000 = 1.2 against 960/120 = 8.
        List<DispatchSequencer.Job<String>> jobs = List.of(
                job("small-soon", NORMAL, 2, 120),
                job("big-later", NORMAL, 5, 2000));

        assertThat(order(DispatchRule.CRITICAL_RATIO, jobs)).containsExactly("big-later", "small-soon");
    }

    @Test
    void criticalRatioPutsOverdueOrdersFirstMostOverdueOfAll() {
        List<DispatchSequencer.Job<String>> jobs = List.of(
                job("behind", NORMAL, 1, 2000),
                job("overdue-a-bit", NORMAL, -1, 60),
                job("overdue-a-lot", NORMAL, -3, 60));

        assertThat(order(DispatchRule.CRITICAL_RATIO, jobs))
                .containsExactly("overdue-a-lot", "overdue-a-bit", "behind");
    }

    @Test
    void atcPicksTheShortJobWhenBothHaveSlack() {
        List<DispatchSequencer.Job<String>> jobs = List.of(
                job("long", NORMAL, 30, 960),
                job("short", NORMAL, 30, 60));

        assertThat(order(DispatchRule.ATC, jobs)).containsExactly("short", "long");
    }

    @Test
    void atcLetsAJobWithNoSlackLeftOvertakeAShorterOne() {
        List<DispatchSequencer.Job<String>> jobs = List.of(
                job("short-with-slack", NORMAL, 30, 120),
                job("long-no-slack", NORMAL, 2, 960));

        assertThat(order(DispatchRule.ATC, jobs)).containsExactly("long-no-slack", "short-with-slack");
    }

    @Test
    void atcWeighsPriority() {
        List<DispatchSequencer.Job<String>> jobs = List.of(
                job("low", LOW, 3, 120),
                job("urgent", URGENT, 3, 120));

        assertThat(order(DispatchRule.ATC, jobs)).containsExactly("urgent", "low");
    }
}
