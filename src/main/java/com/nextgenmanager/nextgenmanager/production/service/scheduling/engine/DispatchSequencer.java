package com.nextgenmanager.nextgenmanager.production.service.scheduling.engine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Puts a batch of jobs in the order a {@link DispatchRule} gives. The sort is stable, so jobs
 * the rule cannot tell apart keep the order they came in.
 */
public final class DispatchSequencer {

    /** ATC look-ahead: how many average job lengths of slack before urgency starts to bite. */
    static final double ATC_K = 2.0;

    /**
     * One job to sequence.
     *
     * @param priorityRank 1 = most urgent
     * @param due          when it is due, or null
     * @param workMinutes  productive minutes of work it needs
     * @param timeToDue    productive minutes between now and the due date on the job's calendar,
     *                     negative when overdue; ignored when {@code due} is null
     */
    public record Job<T>(T ref, int priorityRank, LocalDateTime due, double workMinutes, double timeToDue) {

        boolean hasDue() {
            return due != null;
        }
    }

    private DispatchSequencer() {
    }

    public static <T> List<Job<T>> sequence(List<Job<T>> jobs, DispatchRule rule) {
        List<Job<T>> sorted = new ArrayList<>(jobs);
        sorted.sort(comparator(rule, sorted));
        return sorted;
    }

    private static <T> Comparator<Job<T>> comparator(DispatchRule rule, List<Job<T>> jobs) {
        Comparator<Job<T>> byRank = Comparator.comparingInt(Job::priorityRank);
        Comparator<Job<T>> byDue = Comparator.comparing(Job::due, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Job<T>> dueFirst = Comparator.comparing(j -> !j.hasDue());

        return switch (rule) {
            case PRIORITY -> byRank.thenComparing(byDue);
            case EDD -> byDue.thenComparing(byRank);
            case CRITICAL_RATIO -> dueFirst
                    .thenComparingDouble(DispatchSequencer::criticalRatio)
                    .thenComparing(byRank);
            case ATC -> {
                double averageWork = jobs.stream().mapToDouble(j -> Math.max(j.workMinutes(), 1)).average().orElse(1);
                yield dueFirst
                        .thenComparing(Comparator.comparingDouble((Job<T> j) -> atcIndex(j, averageWork)).reversed())
                        .thenComparing(byDue)
                        .thenComparing(byRank);
            }
        };
    }

    /** Time left over work left. Only meaningful for a job with a due date. */
    static double criticalRatio(Job<?> job) {
        if (!job.hasDue()) return Double.MAX_VALUE;
        double work = Math.max(job.workMinutes(), 1);
        // Every ratio of a job still in time is >= 0. Overdue jobs go below all of them, the most
        // overdue first, rather than being ranked by a ratio that no longer means anything.
        if (job.timeToDue() <= 0) return job.timeToDue() - 1;
        return job.timeToDue() / work;
    }

    /**
     * (weight / work) × e^(−slack⁺ / (k × average work)). Weight 4 for URGENT down to 1 for LOW.
     * Without a due date there is no tardiness to avoid, so the index is the plain weighted
     * shortest-job value and the job sorts after every job that has one.
     */
    static double atcIndex(Job<?> job, double averageWork) {
        double weight = Math.max(1, 5 - job.priorityRank());
        double work = Math.max(job.workMinutes(), 1);
        if (!job.hasDue()) return weight / work;
        double slack = Math.max(0, job.timeToDue() - work);
        return weight / work * Math.exp(-slack / (ATC_K * averageWork));
    }
}
