package tt.edu.uwi.comp3607.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


public final class TelemetryEvent {

    private final EventType type;
    private final Instant timestamp;
    private final Map<String, Object> details;

    public TelemetryEvent(EventType type, Instant timestamp, Map<String, Object> details) {
        this.type = Objects.requireNonNull(type, "type");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        this.details = details == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public EventType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    /** Returns a detail value, or null if it is missing. */
    public Object getDetail(String key) {
        return details.get(key);
    }

    @Override
    public String toString() {
        return timestamp + " " + type + " " + details;
    }
}