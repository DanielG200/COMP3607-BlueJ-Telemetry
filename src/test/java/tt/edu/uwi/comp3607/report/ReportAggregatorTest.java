package tt.edu.uwi.comp3607.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tt.edu.uwi.comp3607.TestClock;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.model.TelemetryEvent;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportAggregatorTest {

    private TestClock clock;
    private EventFactory factory;
    private TelemetrySession session;
    private ReportAggregator aggregator;

    @BeforeEach
    void setUp() {
        clock = TestClock.atEpoch();
        factory = new EventFactory(clock);
        session = new TelemetrySession("816000000", clock);
        aggregator = new ReportAggregator(Duration.ofSeconds(60));
    }

    private void editAfter(long seconds) {
        clock.advanceSeconds(seconds);
        session.record(factory.documentEdit("Dog", 1, 0, seconds * 1000));
    }

    // ---------- Empty session ----------

    @Test
    void emptySessionGivesZeros() {
        assertEquals(0, aggregator.totalCompilationAttempts(session));
        assertEquals(0.0, aggregator.compileSuccessRate(session));
        assertEquals(0.0, aggregator.compileErrorRatio(session));
        assertEquals(Duration.ZERO, aggregator.activeCodingTime(session));
        assertEquals(0.0, aggregator.editsPerActiveMinute(session));
        assertEquals(0, aggregator.compileErrorFrequency(session).size());
        assertEquals(0, aggregator.progressionTimeline(session).size());
    }

    // ---------- Compilation ----------

    @Test
    void successRateAndErrorRatio() {
        session.record(factory.compileSucceeded("A.java"));
        session.record(factory.compileSucceeded("A.java"));
        session.record(factory.compileSucceeded("A.java"));
        session.record(factory.compileFailed("A.java"));

        assertEquals(4, aggregator.totalCompilationAttempts(session));
        assertEquals(3, aggregator.successfulCompilations(session));
        assertEquals(1, aggregator.failedCompilations(session));
        assertEquals(0.75, aggregator.compileSuccessRate(session), 1e-9);
        assertEquals(0.25, aggregator.compileErrorRatio(session), 1e-9);
    }

    @Test
    void compileStartedIsNotCountedAsAnAttempt() {
        session.record(factory.compileStarted("A.java"));
        session.record(factory.compileSucceeded("A.java"));
        assertEquals(1, aggregator.totalCompilationAttempts(session));
    }

    @Test
    void errorFrequencyIsCountedAndSortedMostCommonFirst() {
        session.record(factory.compileError("A.java", 3, "cannot find symbol"));
        session.record(factory.compileError("A.java", 5, "';' expected"));
        session.record(factory.compileError("A.java", 9, "';' expected"));
        session.record(factory.compileError("B.java", 1, "  ';' expected  "));
        session.record(factory.compileError("B.java", 2, null));
        session.record(factory.compileError("B.java", 2, ""));

        Map<String, Integer> frequency = aggregator.compileErrorFrequency(session);

        List<String> order = new ArrayList<>(frequency.keySet());
        assertEquals("';' expected", order.get(0));
        assertEquals(3, (int) frequency.get("';' expected"));
        assertEquals(2, (int) frequency.get("Unknown error"));
        assertEquals(1, (int) frequency.get("cannot find symbol"));
        assertEquals(6, aggregator.totalCompileErrors(session));
    }

    // ---------- Active vs idle time ----------

    @Test
    void shortGapsCountAsActive() {
        editAfter(0);
        editAfter(10);
        editAfter(20);   // gaps of 10s and 20s, both under 60s

        assertEquals(Duration.ofSeconds(30), aggregator.activeCodingTime(session));
        assertEquals(Duration.ZERO, aggregator.idleTime(session));
    }

    @Test
    void longGapsCountAsIdle() {
        editAfter(0);
        editAfter(10);     // active 10s
        editAfter(300);    // 5 minute break: idle
        editAfter(30);     // active 30s

        assertEquals(Duration.ofSeconds(40), aggregator.activeCodingTime(session));
        assertEquals(Duration.ofSeconds(300), aggregator.idleTime(session));
    }

    @Test
    void gapExactlyAtThresholdIsActive() {
        editAfter(0);
        editAfter(60);
        assertEquals(Duration.ofSeconds(60), aggregator.activeCodingTime(session));
    }

    @Test
    void timeWithNoEditsIsAllIdle() {
        clock.advanceSeconds(120);
        session.end();
        assertEquals(Duration.ofSeconds(120), aggregator.idleTime(session));
    }

    @Test
    void activeTimeWorksEvenIfEventsArriveOutOfOrder() {
        TelemetryEvent later = new TelemetryEvent(EventType.DOCUMENT_EDIT,
                clock.instant().plusSeconds(20), null);
        TelemetryEvent earlier = new TelemetryEvent(EventType.DOCUMENT_EDIT,
                clock.instant().plusSeconds(10), null);
        session.record(later);
        session.record(earlier);
        clock.advanceSeconds(20);

        assertEquals(Duration.ofSeconds(10), aggregator.activeCodingTime(session));
    }

    @Test
    void activeTimeNeverExceedsSessionLength() {
        // Edits stamped after the session's end
        session.record(new TelemetryEvent(EventType.DOCUMENT_EDIT,
                clock.instant().plusSeconds(100), null));
        session.record(new TelemetryEvent(EventType.DOCUMENT_EDIT,
                clock.instant().plusSeconds(130), null));
        clock.advanceSeconds(5);
        session.end();

        assertEquals(Duration.ofSeconds(5), aggregator.activeCodingTime(session));
        assertEquals(Duration.ZERO, aggregator.idleTime(session));
    }

    @Test
    void editsPerActiveMinute() {
        editAfter(0);
        for (int i = 0; i < 6; i++) {
            editAfter(10);   // 6 gaps of 10s = 60s active, 7 edits total
        }
        assertEquals(7, aggregator.totalEdits(session));
        assertEquals(7.0, aggregator.editsPerActiveMinute(session), 1e-9);
    }

    // ---------- Lifecycle and execution ----------

    @Test
    void timelineHasOnlyClassesAndMethodsInTimeOrder() {
        session.record(factory.classCreated("Dog"));
        clock.advanceSeconds(5);
        session.record(factory.compileSucceeded("Dog.java"));
        session.record(factory.methodAdded("Dog", "bark() : void"));
        clock.advanceSeconds(5);
        session.record(factory.classCreated("Cat"));

        List<TelemetryEvent> timeline = aggregator.progressionTimeline(session);
        assertEquals(3, timeline.size());
        assertEquals(EventType.CLASS_CREATED, timeline.get(0).getType());
        assertEquals(EventType.METHOD_ADDED, timeline.get(1).getType());
        assertEquals("Cat", timeline.get(2).getDetail("class"));
        assertEquals(2, aggregator.classesCreated(session));
        assertEquals(1, aggregator.methodsAdded(session));
    }

    @Test
    void countsRunsAndExceptions() {
        session.record(factory.programRun("Main", "main"));
        session.record(factory.programRun("Main", "main"));
        session.record(factory.runtimeException("Main", "ArithmeticException", "/ by zero"));

        assertEquals(2, aggregator.programRuns(session));
        assertEquals(1, aggregator.runtimeExceptions(session));
    }

    // ---------- Configuration ----------

    @Test
    void thresholdMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReportAggregator(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new ReportAggregator(Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class,
                () -> new ReportAggregator(null));
    }

    @Test
    void defaultThresholdIsSixtySeconds() {
        ReportAggregator defaults = new ReportAggregator();
        editAfter(0);
        editAfter(60);
        assertEquals(Duration.ofSeconds(60), defaults.activeCodingTime(session));
    }
}