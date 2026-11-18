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
 * Provides one place to collect every event from a BlueJ session, from when
 * BlueJ opens until it closes.
 *
 * This is the centre of our design. The BlueJ listeners don't write any files
 * themselves. They just call {@link #record(TelemetryEvent)}, and when BlueJ
 * closes the exporters read everything from here. Keeping it in one place means
 * each listener only has one job (Single Responsibility), and the JSON and PDF
 * always use the same data.
 *
 * It's also the "subject" in our Observer pattern: any {@link TelemetryObserver}
 * that subscribes is told about each new event.
 *
 * Thread safety: BlueJ calls our listeners from different threads, so adding and
 * reading events is synchronised, and {@link #getEvents()} always gives back a
 * copy. Once {@link #end()} is called, any new events are ignored.
 *
 * Main methods: {@link #record(TelemetryEvent)} to add events and
 * {@link #getEvents()} to read them. One session is made in TelemetryExtension
 * at startup and passed to the listeners, recorders and exporters.
 *
 * @author Kirese Tasha Maharaj
 * @see TelemetryEvent
 * @see TelemetryObserver
 * @see tt.edu.uwi.comp3607.report.ReportAggregator
 */


public class TelemetrySession {

/**
 * The student ID we use when no real one is given. There's no screen to
 * enter a student ID in BlueJ yet, so for now every session uses this.
 */
    public static final String DEFAULT_STUDENT_ID = "STUDENT_ID_PLACEHOLDER";

 /** The student this session belongs to. Never {@code null} or blank. */
    private final String studentId;

 /** Where the session gets the current time from (a fake clock in our tests). */
    private final Clock clock;

/** When the session started, which is basically when BlueJ opened. */
    private final Instant startTime;

/**
 * When the session ended or {@code null} if it's still running. It's
 * {@code volatile} so each thread sees the change as soon as the session ends.
 */
    private volatile Instant endTime;

/**
 * All the events recorded so far, in the order they came in. Only touch
 * this inside a {@code synchronized (events)} block, because BlueJ can add
 * events from more than one thread.
 */
    private final List<TelemetryEvent> events = new ArrayList<>();


/**
 * Everyone who wants to hear about new events. We used a
 * {@link CopyOnWriteArrayList} so observers can be added or removed
 * while we're looping through the list without anything breaking.
 */
    private final List<TelemetryObserver> observers = new CopyOnWriteArrayList<>();


/**
 * Starts a new session right now, using the placeholder student ID and
 * the real system clock. This is the one the plugin uses inside BlueJ.
 */
    public TelemetrySession() {
        this(DEFAULT_STUDENT_ID, Clock.systemUTC());
    }


/**
 * Starts a new session with a specific student ID and clock. We mostly
 * use this in tests so we can control the time exactly.
 *
 * @param studentId the student's ID; if it's {@code null} or blank we use
 *                  {@link #DEFAULT_STUDENT_ID} instead
 * @param clock     where to get the current time from; can't be {@code null}
 * @throws NullPointerException if {@code clock} is {@code null}
 */
    public TelemetrySession(String studentId, Clock clock) {
        this.studentId = (studentId == null || studentId.isBlank())
                ? DEFAULT_STUDENT_ID
                : studentId;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startTime = clock.instant();
    }

/**
 * Adds an event to the session and then tells every observer about it.
 *
 * Side effects: the event is added to this session's list and each observer's
 * {@link TelemetryObserver#onEvent(TelemetryEvent)} is called. If the session has
 * already ended, nothing happens. If an observer throws an exception, we catch
 * it and print it so the other observers (and BlueJ) keep working.
 *
 * @param event the event to add; can't be {@code null}
 * @throws NullPointerException if {@code event} is {@code null}
 */
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


/**
 * Subscribes an observer, so it gets told about every event recorded
 * from now on. Events that were recorded before this aren't sent to it.
 *
 * @param observer the observer to add; can't be {@code null}
 * @throws NullPointerException if {@code observer} is {@code null}
 */
    public void addObserver(TelemetryObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }


    /**
 * Unsubscribes an observer so it stops getting new events. Nothing
 * happens if it wasn't subscribed.
 *
 * @param observer the observer to remove
 */
    public void removeObserver(TelemetryObserver observer) {
        observers.remove(observer);
    }

/**
 * Ends the session and saves the end time. TelemetryExtension calls this once
 * when BlueJ closes. After this, any new events are ignored. Calling it again
 * does nothing, so the first end time is kept.
 */
    public void end() {
        if (endTime == null) {
            endTime = clock.instant();
        }
    }


 /** Returns {@code true} once {@link #end()} has been called. */
    public boolean isEnded() {
        return endTime != null;
    }


/**
 * Returns a read-only copy of all the events recorded so far, oldest first.
 * Because it's a copy, it's safe to loop through even while BlueJ is still
 * adding events. It's empty if nothing has been recorded.
 */
    public List<TelemetryEvent> getEvents() {
        synchronized (events) {
            return Collections.unmodifiableList(new ArrayList<>(events));
        }
    }


/**
 * Returns only the events of one type, e.g. every {@link EventType#COMPILE_ERROR}.
 * The report maths uses this a lot.
 *
 * @param type the kind of event we want
 * @return a new list of matching events, oldest first; empty if there aren't any
 */
    public List<TelemetryEvent> getEventsOfType(EventType type) {
        List<TelemetryEvent> matching = new ArrayList<>();
        for (TelemetryEvent event : getEvents()) {
            if (event.getType() == type) {
                matching.add(event);
            }
        }
        return matching;
    }


/** Returns the student ID, or {@link #DEFAULT_STUDENT_ID} if none was given. */
     public String getStudentId() {
        return studentId;
    }


/** Returns when the session started in UTC, never {@code null}. */
    public Instant getStartTime() {
        return startTime;
    }


/** Returns when the session ended in UTC, or {@code null} if it's still running. */
    public Instant getEndTime() {
        return endTime;
    }


/**
 * Computes how long the session has lasted. If it's still running, this is the
 * time from the start until now. Once it has ended, it stays fixed.
 *
 * @return the length of the session, never negative
 */
    public Duration getDuration() {
        Instant end = endTime != null ? endTime : clock.instant();
        return Duration.between(startTime, end);
    }
}