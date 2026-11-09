package tt.edu.uwi.comp3607.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tt.edu.uwi.comp3607.TestClock;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.model.TelemetryEvent;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionRecorderTest {

    private TelemetrySession session;
    private EventFactory factory;
    private ExecutionRecorder recorder;

    @BeforeEach
    void setUp() {
        TestClock clock = TestClock.atEpoch();
        session = new TelemetrySession("S1", clock);
        factory = new EventFactory(clock);
        recorder = new ExecutionRecorder(session, factory);
    }

    @Test
    void normalRunRecordsOnlyAProgramRun() {
        recorder.recordInvocation("Cat", "main", ExecutionOutcome.NORMAL, null);

        assertEquals(1, session.getEvents().size());
        TelemetryEvent run = session.getEventsOfType(EventType.PROGRAM_RUN).get(0);
        assertEquals("Cat", run.getDetail("class"));
        assertEquals("main", run.getDetail("method"));
    }

    @Test
    void constructorCallUsesAPlaceholderMethodName() {
        recorder.recordInvocation("Cat", null, ExecutionOutcome.NORMAL, null);

        TelemetryEvent run = session.getEventsOfType(EventType.PROGRAM_RUN).get(0);
        assertEquals(ExecutionRecorder.CONSTRUCTOR_NAME, run.getDetail("method"));
    }

    @Test
    void exceptionOutcomeAlsoRecordsARuntimeException() {
        recorder.recordInvocation("Cat", "main", ExecutionOutcome.EXCEPTION, null);

        assertEquals(1, session.getEventsOfType(EventType.PROGRAM_RUN).size());
        assertEquals(1, session.getEventsOfType(EventType.RUNTIME_EXCEPTION).size());
        TelemetryEvent exception = session.getEventsOfType(EventType.RUNTIME_EXCEPTION).get(0);
        assertEquals("Cat", exception.getDetail("class"));
        assertEquals(ExecutionRecorder.UNKNOWN_EXCEPTION_TYPE, exception.getDetail("exception"));
    }

    @Test
    void otherOutcomesDoNotRecordAnException() {
        recorder.recordInvocation("Cat", "main", ExecutionOutcome.TERMINATED, null);
        recorder.recordInvocation("Cat", "main", ExecutionOutcome.UNKNOWN, null);
        recorder.recordInvocation("Cat", "main", null, null);

        assertEquals(3, session.getEventsOfType(EventType.PROGRAM_RUN).size());
        assertEquals(0, session.getEventsOfType(EventType.RUNTIME_EXCEPTION).size());
    }

    @Test
    void textResultIsSummarisedAsConsoleOutput() {
        recorder.recordInvocation("Cat", "speak", ExecutionOutcome.NORMAL, "hello\nworld\n");

        TelemetryEvent output = session.getEventsOfType(EventType.CONSOLE_OUTPUT).get(0);
        assertEquals(2, output.getDetail("lines"));
        assertEquals(12, output.getDetail("characters"));
    }

    @Test
    void emptyResultRecordsNoConsoleOutput() {
        recorder.recordInvocation("Cat", "speak", ExecutionOutcome.NORMAL, "");

        assertEquals(0, session.getEventsOfType(EventType.CONSOLE_OUTPUT).size());
    }

    @Test
    void countLinesHandlesTrailingAndBlankLines() {
        assertEquals(1, ExecutionRecorder.countLines("a"));
        assertEquals(2, ExecutionRecorder.countLines("a\nb"));
        assertEquals(1, ExecutionRecorder.countLines("a\n"));
        assertEquals(1, ExecutionRecorder.countLines("\n"));
        assertEquals(3, ExecutionRecorder.countLines("a\n\nb"));
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new ExecutionRecorder(null, factory));
        assertThrows(NullPointerException.class, () -> new ExecutionRecorder(session, null));
    }
}
