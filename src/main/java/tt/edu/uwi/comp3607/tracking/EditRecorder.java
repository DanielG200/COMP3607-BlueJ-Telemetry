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
 *
 * @author Raymond Sookdeo
 */
public class EditRecorder {

    /** Gap value used for the first edit of a class (there is no previous edit). */
    public static final long NO_PREVIOUS_EDIT = -1;

    private final TelemetrySession session;
    private final EventFactory eventFactory;
    private final Map<String, Long> lastEditMillis = new HashMap<>();

    /**
     * Creates a recorder that writes its events to the given session.
     *
     * @param session      session that receives the DOCUMENT_EDIT events; must not be null
     * @param eventFactory factory used to build the events; must not be null
     * @throws NullPointerException if either argument is null
     */
    public EditRecorder(TelemetrySession session, EventFactory eventFactory) {
        this.session = Objects.requireNonNull(session, "session");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
    }

    /**
     * Records one edit and measures the time since the previous edit of the same class.
     * Edits to different classes are timed separately.
     *
     * @param classKey           unique key of the class, used to look up its previous edit
     * @param className          class name written into the event
     * @param insertedCharacters number of characters added by the edit
     * @param removedCharacters  number of characters deleted by the edit
     * @param nowMillis          time of the edit in epoch milliseconds, supplied by the caller
     */
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

    /** Stops tracking every class, so the next edit of each one has no previous edit. */
    public synchronized void clear() {
        lastEditMillis.clear();
    }
}
