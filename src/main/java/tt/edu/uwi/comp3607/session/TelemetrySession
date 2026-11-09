package tt.edu.uwi.comp3607.session;

import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.model.TelemetryEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The single place where all events from one BlueJ session are collected.
 *
 * Listeners only call record(...). They never write files themselves,
 * which keeps each class to one job (Single Responsibility).
 *
 * This is the "subject" in the Observer pattern: observers are told
 * about every new event.
 */
public class TelemetrySession {

    public static final String DEFAULT_STUDENT_ID = "STUDENT_ID_PLACEHOLDER";

    private final String studentId;
    private final Clock clock;
    private final Instant startTime;
    private volatile Instant endTime;

    private final List<TelemetryEvent> events = new ArrayList<>();
    private final List<TelemetryObserver> observers = new CopyOnWriteArrayList<>();

    public TelemetrySession() {
        this(DEFAULT_STUDENT_ID, Clock.systemUTC());
    }

    public TelemetrySession(String studentId, Clock clock) {
        this.studentId = (studentId == null || studentId.isBlank())
                ? DEFAULT_STUDENT_ID
                : studentId;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startTime = clock.instant();
    }

    /** Adds an event and tells every observer about it. */
    public void record(TelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        if (isEnded()) {
            return; // ignore anything that arrives after shutdown
        }
        synchronized (events) {
            events.add(event);
        }
        for (TelemetryObserver observer : observers) {
            try {
                observer.onEvent(event);
            } catch (RuntimeException exception) {
                // One broken observer must not stop the others or crash BlueJ.
                System.err.println("[Telemetry] Observer failed: " + exception.getMessage());
            }
        }
    }

    public void addObserver(TelemetryObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public void removeObserver(TelemetryObserver observer) {
        observers.remove(observer);
    }

    /** Marks the session as finished. Calling it again has no effect. */
    public void end() {
        if (endTime == null) {
            endTime = clock.instant();
        }
    }

    public boolean isEnded() {
        return endTime != null;
    }

    /** A snapshot copy, so callers can loop over it safely. */
    public List<TelemetryEvent> getEvents() {
        synchronized (events) {
            return Collections.unmodifiableList(new ArrayList<>(events));
        }
    }

    public List<TelemetryEvent> getEventsOfType(EventType type) {
        List<TelemetryEvent> matching = new ArrayList<>();
        for (TelemetryEvent event : getEvents()) {
            if (event.getType() == type) {
                matching.add(event);
            }
        }
        return matching;
    }

    public String getStudentId() {
        return studentId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    /** The end time, or null while the session is still running. */
    public Instant getEndTime() {
        return endTime;
    }

    /** Time from start to end (or to now, if not ended yet). */
    public Duration getDuration() {
        Instant end = endTime != null ? endTime : clock.instant();
        return Duration.between(startTime, end);
    }
}