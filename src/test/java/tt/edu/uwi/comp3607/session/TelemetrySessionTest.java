package tt.edu.uwi.comp3607.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tt.edu.uwi.comp3607.TestClock;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.model.TelemetryEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetrySessionTest {

    private TestClock clock;
    private EventFactory factory;
    private TelemetrySession session;

    @BeforeEach
    void setUp() {
        clock = TestClock.atEpoch();
        factory = new EventFactory(clock);
        session = new TelemetrySession("816000000", clock);
    }

    @Test
    void recordsEventsInOrder() {
        session.record(factory.classCreated("A"));
        session.record(factory.classCreated("B"));

        List<TelemetryEvent> events = session.getEvents();
        assertEquals(2, events.size());
        assertEquals("A", events.get(0).getDetail("class"));
        assertEquals("B", events.get(1).getDetail("class"));
    }

    @Test
    void filtersByType() {
        session.record(factory.classCreated("A"));
        session.record(factory.compileSucceeded("A.java"));
        session.record(factory.classCreated("B"));

        assertEquals(2, session.getEventsOfType(EventType.CLASS_CREATED).size());
        assertEquals(1, session.getEventsOfType(EventType.COMPILE_SUCCEEDED).size());
        assertEquals(0, session.getEventsOfType(EventType.PROGRAM_RUN).size());
    }

    @Test
    void observersAreNotified() {
        List<TelemetryEvent> received = new ArrayList<>();
        TelemetryObserver observer = received::add;
        session.addObserver(observer);

        session.record(factory.classCreated("A"));
        assertEquals(1, received.size());

        session.removeObserver(observer);
        session.record(factory.classCreated("B"));
        assertEquals(1, received.size());
    }

    @Test
    void brokenObserverDoesNotStopOthers() {
        List<TelemetryEvent> received = new ArrayList<>();
        session.addObserver(event -> { throw new RuntimeException("boom"); });
        session.addObserver(received::add);

        assertDoesNotThrow(() -> session.record(factory.classCreated("A")));
        assertEquals(1, received.size());
        assertEquals(1, session.getEvents().size());
    }

    @Test
    void durationGrowsUntilEnded() {
        clock.advanceSeconds(30);
        assertEquals(Duration.ofSeconds(30), session.getDuration());
        assertFalse(session.isEnded());
        assertNull(session.getEndTime());

        session.end();
        clock.advanceSeconds(100);

        assertTrue(session.isEnded());
        assertEquals(Duration.ofSeconds(30), session.getDuration());
    }

    @Test
    void endingTwiceKeepsFirstEndTime() {
        clock.advanceSeconds(10);
        session.end();
        clock.advanceSeconds(10);
        session.end();
        assertEquals(Duration.ofSeconds(10), session.getDuration());
    }

    @Test
    void eventsAfterEndAreIgnored() {
        session.end();
        session.record(factory.classCreated("Late"));
        assertTrue(session.getEvents().isEmpty());
    }

    @Test
    void returnedListCannotBeModified() {
        session.record(factory.classCreated("A"));
        assertThrows(UnsupportedOperationException.class,
                () -> session.getEvents().clear());
    }

    @Test
    void blankStudentIdUsesPlaceholder() {
        assertEquals("816000000", session.getStudentId());
        assertEquals(TelemetrySession.DEFAULT_STUDENT_ID,
                new TelemetrySession("  ", clock).getStudentId());
        assertEquals(TelemetrySession.DEFAULT_STUDENT_ID,
                new TelemetrySession(null, clock).getStudentId());
        assertEquals(TelemetrySession.DEFAULT_STUDENT_ID,
                new TelemetrySession().getStudentId());
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> session.record(null));
        assertThrows(NullPointerException.class, () -> session.addObserver(null));
        assertThrows(NullPointerException.class, () -> new TelemetrySession("x", null));
    }
}