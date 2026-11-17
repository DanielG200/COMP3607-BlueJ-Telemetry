package tt.edu.uwi.comp3607.tracking;

import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.util.Objects;

/**
 * Turns a finished BlueJ invocation (running main, calling a method or
 * creating an object) into telemetry events.
 *
 * BlueJ-free so it can be unit tested. ExecutionListener does the BlueJ part.
 *
 * Note: BlueJ's extension API does not expose the exception type or the
 * program's console output, so the exception is recorded generically and
 * the console summary only covers a text result when BlueJ provides one.
 */
public class ExecutionRecorder {

    /** Method name recorded when BlueJ gives no method name, which means a constructor call. */
    public static final String CONSTRUCTOR_NAME = "<constructor>";

    /** Exception type recorded, because BlueJ does not say which exception was thrown. */
    public static final String UNKNOWN_EXCEPTION_TYPE = "Exception";

    /** Message recorded with every runtime exception, explaining why no details are available. */
    public static final String UNKNOWN_EXCEPTION_MESSAGE =
            "Invocation ended with an exception (details are not exposed by BlueJ)";

    private final TelemetrySession session;
    private final EventFactory eventFactory;

    /**
     * Creates a recorder that writes its events to the given session.
     *
     * @param session      session that receives the events; must not be null
     * @param eventFactory factory used to build the events; must not be null
     * @throws NullPointerException if either argument is null
     */
    public ExecutionRecorder(TelemetrySession session, EventFactory eventFactory) {
        this.session = Objects.requireNonNull(session, "session");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
    }

    /**
     * Records a finished invocation. Always records PROGRAM_RUN, adds RUNTIME_EXCEPTION when it
     * ended with an exception, and adds CONSOLE_OUTPUT when it produced non-empty text.
     *
     * @param className  class that was run
     * @param methodName method name, or null for a constructor call
     * @param outcome    how it ended (null is treated as UNKNOWN)
     * @param resultText any text produced by the call, or null if there was none
     */
    public void recordInvocation(String className, String methodName,
                                 ExecutionOutcome outcome, String resultText) {
        String method = methodName == null ? CONSTRUCTOR_NAME : methodName;
        session.record(eventFactory.programRun(className, method));

        if (outcome == ExecutionOutcome.EXCEPTION) {
            session.record(eventFactory.runtimeException(
                    className, UNKNOWN_EXCEPTION_TYPE, UNKNOWN_EXCEPTION_MESSAGE));
        }

        if (resultText != null && !resultText.isEmpty()) {
            session.record(eventFactory.consoleOutput(
                    countLines(resultText), resultText.length()));
        }
    }

    /** Number of lines in the text. A trailing line break does not add an empty line. */
    static int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && i < text.length() - 1) {
                lines++;
            }
        }
        return lines;
    }
}
