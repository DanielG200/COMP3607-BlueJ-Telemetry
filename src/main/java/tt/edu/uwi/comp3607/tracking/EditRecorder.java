package tt.edu.uwi.comp3607.tracking;

import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Turns raw editor changes into DOCUMENT_EDIT events, working out the
 * time gap since the previous edit of the same class (typing cadence).
 *
 * BlueJ-free so it can be unit tested. The editor listener passes in the
 * current time, which keeps this class deterministic in tests.
 * Source code itself is never recorded, only character counts.
 */
public class EditRecorder {

    /** Gap value used for the first edit of a class (there is no previous edit). */
    public static final long NO_PREVIOUS_EDIT = -1;

    private final TelemetrySession session;
    private final EventFactory eventFactory;
    private final Map<String, Long> lastEditMillis = new HashMap<>();

    public EditRecorder(TelemetrySession session, EventFactory eventFactory) {
        this.session = Objects.requireNonNull(session, "session");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
    }

    public synchronized void recordEdit(String classKey, String className,
                                        int insertedCharacters, int removedCharacters,
                                        long nowMillis) {
        Long previous = lastEditMillis.put(classKey, nowMillis);
        long gap = previous == null ? NO_PREVIOUS_EDIT : nowMillis - previous;

        session.record(eventFactory.documentEdit(
                className, insertedCharacters, removedCharacters, gap));
    }

    /** Stops tracking a class (its editor closed or the class was deleted). */
    public synchronized void forget(String classKey) {
        lastEditMillis.remove(classKey);
    }

    public synchronized void clear() {
        lastEditMillis.clear();
    }
}
