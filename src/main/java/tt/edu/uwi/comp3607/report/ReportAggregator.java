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

/**
 * Computes all the numbers that go into the JSON summary and the PDF report.
 *
 * It only reads a {@link TelemetrySession}. It doesn't save files, draw anything
 * or touch BlueJ, so we could test every calculation with plain JUnit without
 * opening BlueJ. Both exporters are given the same aggregator, so the numbers in
 * the two files always match.
 *
 * How we count active vs idle time: we look at the gap between each pair of
 * edits that are next to each other. If the gap is less than or equal to the
 * idle threshold (60 seconds by default), it counts as active coding. Anything
 * longer counts as idle. Then idle time = session length - active time. For
 * example, typing for 10s, taking a 5 minute break, then typing for 30s gives
 * 40s of active time.
 *
 * Main methods: {@link #compileSuccessRate(TelemetrySession)},
 * {@link #activeCodingTime(TelemetrySession)} and
 * {@link #compileErrorFrequency(TelemetrySession)}. Used by JsonExporter and
 * PdfExporter.
 *
 * @author Kirese Tasha Maharaj
 * @see TelemetrySession
 */

public class ReportAggregator {

    /**
 * How long a gap between edits can be before we count it as idle. We
 * picked 60 seconds because it's long enough for a short pause to think,
 * but short enough that a real break counts as idle.
 */
    public static final Duration DEFAULT_IDLE_THRESHOLD = Duration.ofSeconds(60);

/** The longest gap between edits that still counts as active coding. Always positive. */
    private final Duration idleThreshold;

/**
 * Makes an aggregator that uses the default 60 second idle threshold.
 * This is the one the plugin uses.
 */
    public ReportAggregator() {
        this(DEFAULT_IDLE_THRESHOLD);
    }


/**
 * Makes an aggregator with a custom idle threshold.
 *
 * @param idleThreshold the longest gap between edits that still counts as
 *                      active coding; must be more than zero
 * @throws NullPointerException     if {@code idleThreshold} is {@code null}
 * @throws IllegalArgumentException if {@code idleThreshold} is zero or negative
 */
    public ReportAggregator(Duration idleThreshold) {
        Objects.requireNonNull(idleThreshold, "idleThreshold");
        if (idleThreshold.isNegative() || idleThreshold.isZero()) {
            throw new IllegalArgumentException("idleThreshold must be positive");
        }
        this.idleThreshold = idleThreshold;
    }

    // ---------- Compilation ----------

/**
 * Returns the number of compiles that finished, whether they worked or not.
 * We count COMPILE_SUCCEEDED plus COMPILE_FAILED, and not COMPILE_STARTED, so
 * each compile is only counted once.
 *
 * @param session the session to look at
 * @return the number of finished compiles, or 0 if there weren't any
 */
    public int totalCompilationAttempts(TelemetrySession session) {
        return successfulCompilations(session) + failedCompilations(session);
    }


/**
 * Returns the number of compiles that finished with no errors.
 *
 * @param session the session to look at
 * @return the number of successful compiles
 */
    public int successfulCompilations(TelemetrySession session) {
        return count(session, EventType.COMPILE_SUCCEEDED);
    }


/**
 * Returns the number of compiles that finished with at least one error.
 *
 * @param session the session to look at
 * @return the number of failed compiles
 */
    public int failedCompilations(TelemetrySession session) {
        return count(session, EventType.COMPILE_FAILED);
    }

   
 /**
 * Counts every single compile error message. This can be bigger than
 * {@link #failedCompilations(TelemetrySession)}, because one failed
 * compile can have several errors.
 *
 * @param session the session to look at
 * @return the total number of error messages
 */
    public int totalCompileErrors(TelemetrySession session) {
        return count(session, EventType.COMPILE_ERROR);
    }


   /**
 * Returns the number of individual compile error messages. This can be bigger
 * than {@link #failedCompilations(TelemetrySession)}, because one failed compile
 * can have several errors.
 *
 * @param session the session to look at
 * @return the total number of error messages
 */
    public double compileSuccessRate(TelemetrySession session) {
        int total = totalCompilationAttempts(session);
        return total == 0 ? 0.0 : (double) successfulCompilations(session) / total;
    }

/**
 * Works out what fraction of compiles failed. This is the opposite of
 * {@link #compileSuccessRate(TelemetrySession)}, so 1 failed out of 4 gives {@code 0.25}.
 *
 * @param session the session to look at
 * @return a number from 0.0 to 1.0, or 0.0 if there were no compiles
 */
    public double compileErrorRatio(TelemetrySession session) {
        int total = totalCompilationAttempts(session);
        return total == 0 ? 0.0 : (double) failedCompilations(session) / total;
    }


/**
 * Computes how many times each different compile error came up, with the most
 * common one first. This is what the error chart in the PDF uses.
 *
 * BlueJ doesn't give us error codes, so we group errors by their message. Extra
 * spaces at the start or end are ignored, and an empty or missing message is
 * counted as {@code "Unknown error"}. Example result:
 * {@code {"';' expected"=3, "cannot find symbol"=1}}
 *
 * @param session the session to look at
 * @return each error message with how many times it happened, most common first;
 *         empty if there were no errors
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

/**
 * Returns the number of times something was typed or deleted in the editor.
 *
 * @param session the session to look at
 * @return the number of edits
 */
    public int totalEdits(TelemetrySession session) {
        return count(session, EventType.DOCUMENT_EDIT);
    }


/**
 * Computes how much of the session was spent actively coding, using the idle
 * threshold rule explained at the top of this class. We sort the edits by time
 * first, so it still works if events arrive slightly out of order. The answer is
 * never longer than the session itself.
 *
 * @param session the session to look at
 * @return the total active time; zero if there were fewer than two edits
 */
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


/**
 * Computes how much of the session was idle, which is the session length minus
 * the active coding time. If there were no edits at all, the whole session
 * counts as idle.
 *
 * @param session the session to look at
 * @return the idle time, never negative
 */
    public Duration idleTime(TelemetrySession session) {
        Duration idle = session.getDuration().minus(activeCodingTime(session));
        return idle.isNegative() ? Duration.ZERO : idle;
    }




/**
 * Computes roughly how fast the student was typing: the number of edits divided
 * by the minutes of active coding.
 *
 * @param session the session to look at
 * @return edits per active minute, or 0.0 if there was no active time
 */    
    public double editsPerActiveMinute(TelemetrySession session) {
        double minutes = activeCodingTime(session).toMillis() / 60_000.0;
        return minutes == 0 ? 0.0 : totalEdits(session) / minutes;
    }

    // ---------- Lifecycle ----------

/**
 * Returns the number of new classes made during the session. Classes that were
 * already in the project when it was opened don't count.
 *
 * @param session the session to look at
 * @return the number of new classes
 */
    public int classesCreated(TelemetrySession session) {
        return count(session, EventType.CLASS_CREATED);
    }

/**
 * Returns the number of new methods found after compiling.
 *
 * @param session the session to look at
 * @return the number of methods added
 */
    public int methodsAdded(TelemetrySession session) {
        return count(session, EventType.METHOD_ADDED);
    }

/**
 * Returns every class creation and method addition in time order. The PDF uses
 * this for the timeline table, so you can see how the student's code grew
 * during the session.
 *
 * @param session the session to look at
 * @return the CLASS_CREATED and METHOD_ADDED events, oldest first; empty if
 *         there weren't any
 */
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
/**
 * Returns the number of times the student ran something (a method, {@code main}
 * or a constructor).
 *
 * @param session the session to look at
 * @return the number of runs
 */
    public int programRuns(TelemetrySession session) {
        return count(session, EventType.PROGRAM_RUN);
    }


/**
 * Returns the number of runs that crashed with an exception.
 *
 * @param session the session to look at
 * @return the number of runtime exceptions
 */
    public int runtimeExceptions(TelemetrySession session) {
        return count(session, EventType.RUNTIME_EXCEPTION);
    }

    // ---------- Helpers ----------

/**
 * Counts the events of one type. Most of the counting methods above just call
 * this.
 *
 * @param session the session to look at
 * @param type    the kind of event to count
 * @return how many events of that type there are
 */
    private int count(TelemetrySession session, EventType type) {
        return session.getEventsOfType(type).size();
    }


/**
 * Returns a copy of the list sorted from oldest to newest. We copy it so the
 * original list isn't changed.
 *
 * @param events the events to sort
 * @return a new sorted list
 */
    private static List<TelemetryEvent> sortedByTime(List<TelemetryEvent> events) {
        List<TelemetryEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(TelemetryEvent::getTimestamp));
        return sorted;
    }


/**
 * Cleans up an error message so the same error is always counted together.
 * It trims extra spaces, and turns an empty or missing message into
 * {@code "Unknown error"}.
 *
 * @param message the message from the compile error event (might be {@code null})
 * @return the cleaned up message, never {@code null} or empty
 */
    private static String normaliseMessage(Object message) {
        if (message == null) {
            return "Unknown error";
        }
        String text = message.toString().trim();
        return text.isEmpty() ? "Unknown error" : text;
    }


/**
 * Sorts the error counts so the most common error comes first. We return
 * a {@link LinkedHashMap} so it keeps this order when we loop through it.
 *
 * @param counts each error message with how many times it happened
 * @return a new map with the same entries, biggest count first
 */
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