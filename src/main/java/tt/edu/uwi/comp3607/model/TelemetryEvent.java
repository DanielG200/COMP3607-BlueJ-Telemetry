package tt.edu.uwi.comp3607.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores one thing that happened during a BlueJ session, for example
 * "compile error on line 7 of Dog.java at 2:01pm".
 *
 * Every event has three parts: a {@link EventType type} (what happened), a
 * timestamp (when it happened, in UTC) and a map of details, which changes
 * depending on the type. For example, a compile error has {@code file},
 * {@code line} and {@code message}.
 *
 * Once an event is made it can't be changed (it's immutable). We did this
 * because BlueJ calls our listeners from different threads, and an object that
 * never changes is safe to share between them. We even copy the details map,
 * so changing the map you passed in won't change the event.
 *
 * Get events from {@link EventFactory} instead of calling the constructor
 * yourself, so every event of the same type has the same detail keys. Used by
 * TelemetrySession (stores them), ReportAggregator (counts them) and
 * JsonExporter (writes them to the file).
 *
 * @author Kirese Tasha Maharaj
 * @see EventFactory
 * @see tt.edu.uwi.comp3607.session.TelemetrySession#record(TelemetryEvent)
 */


public final class TelemetryEvent {

    /** What kind of action this event is. Never {@code null}. */
    private final EventType type;

    /** When the action happened, in UTC. Never {@code null}. */
    private final Instant timestamp;
    /**
 *Note about the action. We keep the details in the order they were
 * added so the JSON file is easier to read. This map is read-only, and it is
 * empty (not {@code null}) if there aren't any details.
 */
    private final Map<String, Object> details;

    /**
 * Makes a new event.
 *
 * @param type      what happened, can't be {@code null}
 * @param timestamp when it happened, can't be {@code null}
 * @param details   extra info about the event or {@code null} if there isn't any.
 *                  We copy this map so changing it afterwards does not affect the event.
 * @throws NullPointerException if {@code type} or {@code timestamp} is {@code null}
 */

    public TelemetryEvent(EventType type, Instant timestamp, Map<String, Object> details) {
        this.type = Objects.requireNonNull(type, "type");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.details = details == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }


    /** Returns what kind of action this event is, never {@code null}. */
    public EventType getType() {
        return type;
    }


  /** Returns when the action happened in UTC, never {@code null}. */
     public Instant getTimestamp() {
        return timestamp;
    }


   /**
 * Returns all the extra details for this event, in the order they were added.
 * The map is read-only (changing it throws an {@link UnsupportedOperationException})
 * and it's empty if there are no details.
 */
    public Map<String, Object> getDetails() {
        return details;
    }


   /**
 * Returns one detail using its key, e.g. {@code getDetail("line")} for a compile error.
 *
 * @param key the name of the detail we want
 * @return the value for that key, or {@code null} if this event doesn't have it
 */
     public Object getDetail(String key) {
        return details.get(key);
    }


    /**
 * Returns a short, readable version of the event for debugging, e.g.
 * {@code 2026-11-09T10:00:00Z CLASS_CREATED {class=Dog}}.
 */
    @Override
    public String toString() {
        return timestamp + " " + type + " " + details;
    }
}