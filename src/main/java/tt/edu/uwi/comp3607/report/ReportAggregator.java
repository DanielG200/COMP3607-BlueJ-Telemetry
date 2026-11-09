package tt.edu.uwi.comp3607.report;

import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.model.TelemetryEvent;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class ReportAggregator {

    public static final Duration DEFAULT_IDLE_THRESHOLD = Duration.ofSeconds(60);

    private final Duration idleThreshold;

    public ReportAggregator() {
        this(DEFAULT_IDLE_THRESHOLD);
    }

    public ReportAggregator(Duration idleThreshold) {
        Objects.requireNonNull(idleThreshold, "idleThreshold");
        if (idleThreshold.isNegative() || idleThreshold.isZero()) {
            throw new IllegalArgumentException("idleThreshold must be positive");
        }
        this.idleThreshold = idleThreshold;
    }

    // ---------- Compilation ----------

    /** One attempt = one finished compile (succeeded or failed). */
    public int totalCompilationAttempts(TelemetrySession session) {
        return successfulCompilations(session) + failedCompilations(session);
    }

    public int successfulCompilations(TelemetrySession session) {
        return count(session, EventType.COMPILE_SUCCEEDED);
    }

    public int failedCompilations(TelemetrySession session) {
        return count(session, EventType.COMPILE_FAILED);
    }

    public int totalCompileErrors(TelemetrySession session) {
        return count(session, EventType.COMPILE_ERROR);
    }

    /** Successful / total attempts, from 0.0 to 1.0. Returns 0.0 if no attempts. */
    public double compileSuccessRate(TelemetrySession session) {
        int total = totalCompilationAttempts(session);
        return total == 0 ? 0.0 : (double) successfulCompilations(session) / total;
    }

    /** Failed / total attempts, from 0.0 to 1.0. Returns 0.0 if no attempts. */
    public double compileErrorRatio(TelemetrySession session) {
        int total = totalCompilationAttempts(session);
        return total == 0 ? 0.0 : (double) failedCompilations(session) / total;
    }

    /**
     * How often each compile error message appeared, most common first.
     * Example: {"';' expected" = 4, "cannot find symbol" = 2}
     */
    public Map<String, Integer> compileErrorFrequency(TelemetrySession session) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TelemetryEvent event : session.getEventsOfType(EventType.COMPILE_ERROR)) {
            String message = normaliseMessage(event.getDetail("message"));
            counts.merge(message, 1, Integer::sum);
        }
        return sortByCountDescending(counts);
    }

    // ---------- Editor ----------

    public int totalEdits(TelemetrySession session) {
        return count(session, EventType.DOCUMENT_EDIT);
    }

    public Duration activeCodingTime(TelemetrySession session) {
        List<TelemetryEvent> edits = sortedByTime(
                session.getEventsOfType(EventType.DOCUMENT_EDIT));

        Duration active = Duration.ZERO;
        for (int i = 1; i < edits.size(); i++) {
            Duration gap = Duration.between(
                    edits.get(i - 1).getTimestamp(),
                    edits.get(i).getTimestamp());
            if (!gap.isNegative() && gap.compareTo(idleThreshold) <= 0) {
                active = active.plus(gap);
            }
        }

        Duration sessionLength = session.getDuration();
        return active.compareTo(sessionLength) > 0 ? sessionLength : active;
    }

    public Duration idleTime(TelemetrySession session) {
        Duration idle = session.getDuration().minus(activeCodingTime(session));
        return idle.isNegative() ? Duration.ZERO : idle;
    }

    /** Edits per minute of active coding time. Returns 0.0 if no active time. */
    public double editsPerActiveMinute(TelemetrySession session) {
        double minutes = activeCodingTime(session).toMillis() / 60_000.0;
        return minutes == 0 ? 0.0 : totalEdits(session) / minutes;
    }

    // ---------- Lifecycle ----------

    public int classesCreated(TelemetrySession session) {
        return count(session, EventType.CLASS_CREATED);
    }

    public int methodsAdded(TelemetrySession session) {
        return count(session, EventType.METHOD_ADDED);
    }

    /** Class creations and method additions in time order, for the PDF timeline. */
    public List<TelemetryEvent> progressionTimeline(TelemetrySession session) {
        List<TelemetryEvent> timeline = new ArrayList<>();
        for (TelemetryEvent event : session.getEvents()) {
            if (event.getType() == EventType.CLASS_CREATED
                    || event.getType() == EventType.METHOD_ADDED) {
                timeline.add(event);
            }
        }
        return sortedByTime(timeline);
    }

    // ---------- Execution ----------

    public int programRuns(TelemetrySession session) {
        return count(session, EventType.PROGRAM_RUN);
    }

    public int runtimeExceptions(TelemetrySession session) {
        return count(session, EventType.RUNTIME_EXCEPTION);
    }

    // ---------- Helpers ----------

    private int count(TelemetrySession session, EventType type) {
        return session.getEventsOfType(type).size();
    }

    private static List<TelemetryEvent> sortedByTime(List<TelemetryEvent> events) {
        List<TelemetryEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(TelemetryEvent::getTimestamp));
        return sorted;
    }

    private static String normaliseMessage(Object message) {
        if (message == null) {
            return "Unknown error";
        }
        String text = message.toString().trim();
        return text.isEmpty() ? "Unknown error" : text;
    }

    private static Map<String, Integer> sortByCountDescending(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }
}