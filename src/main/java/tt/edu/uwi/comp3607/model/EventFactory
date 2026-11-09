package tt.edu.uwi.comp3607.model;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DESIGN PATTERN: Factory.
 *
 * The only place that builds TelemetryEvent objects. Listeners call a
 * method such as compileError() instead of filling in maps themselves
 * so every event of the same type always has the same detail keys.
 *
 * The clock is used to track time.
 */
public class EventFactory {

    private final Clock clock;

    public EventFactory() {
        this(Clock.systemUTC());
    }

    public EventFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** General method for any event type. */
    public TelemetryEvent create(EventType type, Map<String, Object> details) {
        return new TelemetryEvent(type, clock.instant(), details);
    }

    public TelemetryEvent create(EventType type) {
        return create(type, null);
    }

    // ---------- Lifecycle ----------

    public TelemetryEvent projectOpened(String projectName) {
        return create(EventType.PROJECT_OPENED, details("project", projectName));
    }

    public TelemetryEvent projectClosed(String projectName) {
        return create(EventType.PROJECT_CLOSED, details("project", projectName));
    }

    public TelemetryEvent classCreated(String className) {
        return create(EventType.CLASS_CREATED, details("class", className));
    }

    public TelemetryEvent classModified(String className) {
        return create(EventType.CLASS_MODIFIED, details("class", className));
    }

    public TelemetryEvent classRenamed(String oldName, String newName) {
        return create(EventType.CLASS_RENAMED,
                details("oldName", oldName, "newName", newName));
    }

    public TelemetryEvent classRemoved(String className) {
        return create(EventType.CLASS_REMOVED, details("class", className));
    }

    public TelemetryEvent methodAdded(String className, String signature) {
        return create(EventType.METHOD_ADDED,
                details("class", className, "method", signature));
    }

    public TelemetryEvent methodRemoved(String className, String signature) {
        return create(EventType.METHOD_REMOVED,
                details("class", className, "method", signature));
    }

    // ---------- Editor ----------

    public TelemetryEvent documentEdit(String className, int inserted, int removed,
                                       long gapSincePreviousEditMs) {
        return create(EventType.DOCUMENT_EDIT, details(
                "class", className,
                "insertedCharacters", inserted,
                "removedCharacters", removed,
                "gapSincePreviousEditMs", gapSincePreviousEditMs));
    }

    // ---------- Compilation ----------

    public TelemetryEvent compileStarted(String file) {
        return create(EventType.COMPILE_STARTED, details("file", file));
    }

    public TelemetryEvent compileError(String file, int line, String message) {
        return create(EventType.COMPILE_ERROR,
                details("file", file, "line", line, "message", message));
    }

    public TelemetryEvent compileWarning(String file, int line, String message) {
        return create(EventType.COMPILE_WARNING,
                details("file", file, "line", line, "message", message));
    }

    public TelemetryEvent compileSucceeded(String file) {
        return create(EventType.COMPILE_SUCCEEDED, details("file", file));
    }

    public TelemetryEvent compileFailed(String file) {
        return create(EventType.COMPILE_FAILED, details("file", file));
    }

    // ---------- Execution ----------

    public TelemetryEvent programRun(String className, String methodName) {
        return create(EventType.PROGRAM_RUN,
                details("class", className, "method", methodName));
    }

    public TelemetryEvent runtimeException(String className, String exceptionType,
                                           String message) {
        return create(EventType.RUNTIME_EXCEPTION, details(
                "class", className,
                "exception", exceptionType,
                "message", message));
    }

    public TelemetryEvent consoleOutput(int lineCount, int characterCount) {
        return create(EventType.CONSOLE_OUTPUT,
                details("lines", lineCount, "characters", characterCount));
    }

    /** Builds an ordered map **/
    private static Map<String, Object> details(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}