package tt.edu.uwi.comp3607;

import bluej.extensions2.event.InvocationFinishedEvent;
import bluej.extensions2.event.InvocationFinishedListener;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.session.TelemetrySession;
import tt.edu.uwi.comp3607.tracking.ExecutionOutcome;
import tt.edu.uwi.comp3607.tracking.ExecutionRecorder;

/**
 * Listens for finished program runs and method calls in BlueJ and passes
 * them to ExecutionRecorder. This class is only the BlueJ adapter.
 *
 * @author Raymond Sookdeo
 */
public class ExecutionListener implements InvocationFinishedListener {

    private final ExecutionRecorder recorder;

    /**
     * Creates a listener that records finished runs in the given session.
     *
     * @param session      session that receives the events
     * @param eventFactory factory used to build the events
     */
    public ExecutionListener(TelemetrySession session, EventFactory eventFactory) {
        this.recorder = new ExecutionRecorder(session, eventFactory);
    }

    /**
     * Called by BlueJ when a method call, constructor call or program run finishes. Passes the details
     * to the recorder. A failure here is printed and never reaches BlueJ.
     */
    @Override
    public void invocationFinished(InvocationFinishedEvent event) {
        try {
            recorder.recordInvocation(
                    event.getClassName(),
                    event.getMethodName(),
                    toOutcome(event.getEventType()),
                    resultText(event.getResult()));
        } catch (RuntimeException exception) {
            System.err.println("[Telemetry] Could not record execution: " + exception.getMessage());
        }
    }

    /** Converts BlueJ's event type into our own outcome. A null or unrecognised type becomes UNKNOWN. */
    static ExecutionOutcome toOutcome(InvocationFinishedEvent.EventType type) {
        if (type == null) {
            return ExecutionOutcome.UNKNOWN;
        }
        switch (type) {
            case NORMAL_EXIT:
                return ExecutionOutcome.NORMAL;
            case EXCEPTION_EXIT:
                return ExecutionOutcome.EXCEPTION;
            case TERMINATED_EXIT:
                return ExecutionOutcome.TERMINATED;
            default:
                return ExecutionOutcome.UNKNOWN;
        }
    }

    /** Returns the result as text if it is text, a number or a boolean. Returns null for anything else, such as BlueJ objects. */
    private static String resultText(Object result) {
        if (result instanceof CharSequence || result instanceof Number || result instanceof Boolean) {
            return result.toString();
        }
        return null;
    }
}
