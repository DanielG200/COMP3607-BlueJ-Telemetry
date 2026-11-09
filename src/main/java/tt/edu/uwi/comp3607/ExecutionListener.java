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
 */
public class ExecutionListener implements InvocationFinishedListener {

    private final ExecutionRecorder recorder;

    public ExecutionListener(TelemetrySession session, EventFactory eventFactory) {
        this.recorder = new ExecutionRecorder(session, eventFactory);
    }

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

    /** Only plain text or numbers count as output. Objects (BObject) are ignored. */
    private static String resultText(Object result) {
        if (result instanceof CharSequence || result instanceof Number || result instanceof Boolean) {
            return result.toString();
        }
        return null;
    }
}
