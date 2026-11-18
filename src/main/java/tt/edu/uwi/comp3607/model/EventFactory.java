package tt.edu.uwi.comp3607.model;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Creates every {@link TelemetryEvent} in our plugin, so events of the same
 * type always have the same detail keys.
 *
 * Design pattern: Factory. Instead of every listener building its own details
 * map, they call a method here like {@code compileError(file, line, message)}.
 * Without this, it would be easy for one listener to use {@code "line"} and
 * another to use {@code "lineNumber"}, which would break the report.
 *
 * The factory also stamps each event with the current time from a
 * {@link Clock}. In real use that's the system clock, but in our tests we pass
 * in a fake clock so the times are exact every time.
 *
 * One factory is made in TelemetryExtension at startup and shared with all the
 * listeners and recorders. Example:
 * <pre>{@code
 * EventFactory factory = new EventFactory();
 * session.record(factory.compileError("Dog.java", 7, "';' expected"));
 * }</pre>
 *
 * @author Kirese Tasha Maharaj
 * @see TelemetryEvent
 * @see EventType
 */
public class EventFactory {

/** Where we get the current time from when stamping new events. */
    private final Clock clock;

/**
 * Makes a factory that uses the real system clock (UTC).
 * This is the one the plugin uses inside BlueJ.
 */
    public EventFactory() {
        this(Clock.systemUTC());
    }

    /**
 * Makes a factory that uses the clock you give it. This is mostly for
 * tests, so we can control exactly what time each event gets.
 *
 * @param clock where to get the current time from; can't be {@code null}
 * @throws NullPointerException if {@code clock} is {@code null}
 */
    public EventFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
 * Makes an event of any type, stamped with the current time. The other
 * methods in this class all use this one. Use it directly only if there
 * isn't a more specific method for the type you want.
 *
 * @param type    what happened; can't be {@code null}
 * @param details extra info, or {@code null} for none
 * @return the new event
 * @throws NullPointerException if {@code type} is {@code null}
 */
    public TelemetryEvent create(EventType type, Map<String, Object> details) {
        return new TelemetryEvent(type, clock.instant(), details);
    }

    public TelemetryEvent create(EventType type) {
        return create(type, null);
    }

    // ---------- Lifecycle ----------


/**
 * Makes a {@link EventType#PROJECT_OPENED} event.
 *
 * @param projectName name of the project that was opened
 * @return the new event, with the detail {@code project}
 */
    public TelemetryEvent projectOpened(String projectName) {
        return create(EventType.PROJECT_OPENED, details("project", projectName));
    }

    /**
 * Makes a {@link EventType#PROJECT_CLOSED} event.
 *
 * @param projectName name of the project that was closed
 * @return the new event with detail {@code project}
 */
    public TelemetryEvent projectClosed(String projectName) {
        return create(EventType.PROJECT_CLOSED, details("project", projectName));
    }

    /**
 * Makes a {@link EventType#CLASS_CREATED} event.
 *
 * @param className name of the new class
 * @return the new event with detail {@code class}
 */
    public TelemetryEvent classCreated(String className) {
        return create(EventType.CLASS_CREATED, details("class", className));
    }

/**
 * Makes a {@link EventType#CLASS_MODIFIED} event.
 *
 * @param className name of the class that was edited
 * @return the new event with the detail {@code class}
 */
    public TelemetryEvent classModified(String className) {
        return create(EventType.CLASS_MODIFIED, details("class", className));
    }


/**
 * Makes a {@link EventType#CLASS_RENAMED} event. We keep both names so you
 * can still follow the class in the report after it's renamed.
 *
 * @param oldName what the class used to be called
 * @param newName what the class is called now
 * @return the new event with details {@code oldName} and {@code newName}
 */
    public TelemetryEvent classRenamed(String oldName, String newName) {
        return create(EventType.CLASS_RENAMED,
                details("oldName", oldName, "newName", newName));
    }


/**
 * Makes a {@link EventType#CLASS_REMOVED} event.
 *
 * @param className name of the class that was deleted
 * @return the new event with detail {@code class}
 */
    public TelemetryEvent classRemoved(String className) {
        return create(EventType.CLASS_REMOVED, details("class", className));
    }

/**
 * Makes a {@link EventType#METHOD_ADDED} event.
 *
 * @param className the class the method belongs to
 * @param signature the method for e.g. {@code "area() : double"}
 * @return the new event, with details {@code class} and {@code method}
 */
    public TelemetryEvent methodAdded(String className, String signature) {
        return create(EventType.METHOD_ADDED,
                details("class", className, "method", signature));
    }


/**
 * Makes a {@link EventType#METHOD_REMOVED} event.
 *
 * @param className the class the method used to belong to
 * @param signature the method that was removed for e.g. {@code "area() : double"}
 * @return the new event, with details {@code class} and {@code method}
 */
    public TelemetryEvent methodRemoved(String className, String signature) {
        return create(EventType.METHOD_REMOVED,
                details("class", className, "method", signature));
    }

    // ---------- Editor ----------


/**
 * Makes a {@link EventType#DOCUMENT_EDIT} event for one change in the editor.
 * We only save how many characters changed but never the actual code.
 *
 * @param className              the class being edited
 * @param inserted               how many characters were typed
 * @param removed                how many characters were deleted
 * @param gapSincePreviousEditMs milliseconds since the last edit in this class,
 *                               or {@code -1} if this is the first edit
 * @return the new event, with details {@code class}, {@code insertedCharacters},
 *         {@code removedCharacters} and {@code gapSincePreviousEditMs}
 */
    public TelemetryEvent documentEdit(String className, int inserted, int removed,
                                       long gapSincePreviousEditMs) {
        return create(EventType.DOCUMENT_EDIT, details(
                "class", className,
                "insertedCharacters", inserted,
                "removedCharacters", removed,
                "gapSincePreviousEditMs", gapSincePreviousEditMs));
    }

    // ---------- Compilation ----------


/**
 * Makes a {@link EventType#COMPILE_STARTED} event.
 *
 * @param file name of the file being compiled, or {@code null} if BlueJ didn't say
 * @return the new event with the detail {@code file}
 */
    public TelemetryEvent compileStarted(String file) {
        return create(EventType.COMPILE_STARTED, details("file", file));
    }

/**
 * Makes a {@link EventType#COMPILE_ERROR} event. The message is what we use
 * later to count which errors happen the most.
 *
 * @param file    the file with the error
 * @param line    the line number of the error
 * @param message what the compiler said, e.g. {@code "';' expected"}
 * @return the new event with the details {@code file}, {@code line} and {@code message}
 */
    public TelemetryEvent compileError(String file, int line, String message) {
        return create(EventType.COMPILE_ERROR,
                details("file", file, "line", line, "message", message));
    }

    
    /**
 * Makes a {@link EventType#COMPILE_WARNING} event.
 *
 * @param file    the file with the warning
 * @param line    the line number of the warning
 * @param message what the compiler said
 * @return the new event with the details {@code file}, {@code line} and {@code message}
 */
    public TelemetryEvent compileWarning(String file, int line, String message) {
        return create(EventType.COMPILE_WARNING,
                details("file", file, "line", line, "message", message));
    }


/**
 * Makes a {@link EventType#COMPILE_SUCCEEDED} event.
 *
 * @param file name of the file that compiled
 * @return the new event with detail {@code file}
 */
    public TelemetryEvent compileSucceeded(String file) {
        return create(EventType.COMPILE_SUCCEEDED, details("file", file));
    }

   
   /**
 * Makes a {@link EventType#COMPILE_FAILED} event.
 *
 * @param file name of the file that failed to compile
 * @return the new event, with detail {@code file}
 */
    public TelemetryEvent compileFailed(String file) {
        return create(EventType.COMPILE_FAILED, details("file", file));
    }

    // ---------- Execution ----------
/**
 * Makes a {@link EventType#PROGRAM_RUN} event.
 *
 * @param className  the class the student ran something from
 * @param methodName the method that was called, or {@code "<constructor>"}
 *                   if they created an object
 * @return the new event, with details {@code class} and {@code method}
 */
    public TelemetryEvent programRun(String className, String methodName) {
        return create(EventType.PROGRAM_RUN,
                details("class", className, "method", methodName));
    }


    /**
 * Makes a {@link EventType#RUNTIME_EXCEPTION} event.
 *
 * @param className     the class where the run crashed
 * @param exceptionType the type of exception, if BlueJ tells us
 * @param message       the exception message, if there is one
 * @return the new event, with details {@code class}, {@code exception} and {@code message}
 */
    public TelemetryEvent runtimeException(String className, String exceptionType,
                                           String message) {
        return create(EventType.RUNTIME_EXCEPTION, details(
                "class", className,
                "exception", exceptionType,
                "message", message));
    }


    /**
 * Makes a {@link EventType#CONSOLE_OUTPUT} event. We only save a summary, not the output itself.
 *
 * @param lineCount      number of lines of output
 * @param characterCount number of characters of output
 * @return the new event, with details {@code lines} and {@code characters}
 */
    public TelemetryEvent consoleOutput(int lineCount, int characterCount) {
        return create(EventType.CONSOLE_OUTPUT,
                details("lines", lineCount, "characters", characterCount));
    }


    /**
 * Turns a list like {@code "file", "Dog.java", "line", 7} into a map. Every
 * even position is a key and the next one is its value. We use a
 * {@link LinkedHashMap} so the details stay in the order we wrote them.
 *
 * @param pairs keys and values, one after the other
 * @return a new map with the keys and values in order
 */
    private static Map<String, Object> details(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}