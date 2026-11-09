package tt.edu.uwi.comp3607.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tt.edu.uwi.comp3607.TestClock;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFactoryTest {

    private TestClock clock;
    private EventFactory factory;

    @BeforeEach
    void setUp() {
        clock = TestClock.atEpoch();
        factory = new EventFactory(clock);
    }

    @Test
    void eventUsesClockTime() {
        TelemetryEvent event = factory.create(EventType.SESSION_STARTED);
        assertEquals(clock.instant(), event.getTimestamp());
        assertEquals(EventType.SESSION_STARTED, event.getType());
        assertTrue(event.getDetails().isEmpty());
    }

    @Test
    void compileErrorHasFileLineAndMessage() {
        TelemetryEvent event = factory.compileError("Dog.java", 12, "';' expected");
        assertEquals(EventType.COMPILE_ERROR, event.getType());
        assertEquals("Dog.java", event.getDetail("file"));
        assertEquals(12, event.getDetail("line"));
        assertEquals("';' expected", event.getDetail("message"));
    }

    @Test
    void documentEditMapsCharacterCounts() {
        TelemetryEvent event = factory.documentEdit("Dog", 5, 2, 300L);
        assertEquals(EventType.DOCUMENT_EDIT, event.getType());
        assertEquals(5, event.getDetail("insertedCharacters"));
        assertEquals(2, event.getDetail("removedCharacters"));
        assertEquals(300L, event.getDetail("gapSincePreviousEditMs"));
    }

    @Test
    void everyHelperProducesTheMatchingType() {
        assertEquals(EventType.PROJECT_OPENED, factory.projectOpened("P").getType());
        assertEquals(EventType.PROJECT_CLOSED, factory.projectClosed("P").getType());
        assertEquals(EventType.CLASS_CREATED, factory.classCreated("A").getType());
        assertEquals(EventType.CLASS_MODIFIED, factory.classModified("A").getType());
        assertEquals(EventType.CLASS_RENAMED, factory.classRenamed("A", "B").getType());
        assertEquals(EventType.CLASS_REMOVED, factory.classRemoved("A").getType());
        assertEquals(EventType.METHOD_ADDED, factory.methodAdded("A", "m()").getType());
        assertEquals(EventType.METHOD_REMOVED, factory.methodRemoved("A", "m()").getType());
        assertEquals(EventType.COMPILE_STARTED, factory.compileStarted("A.java").getType());
        assertEquals(EventType.COMPILE_WARNING, factory.compileWarning("A.java", 1, "w").getType());
        assertEquals(EventType.COMPILE_SUCCEEDED, factory.compileSucceeded("A.java").getType());
        assertEquals(EventType.COMPILE_FAILED, factory.compileFailed("A.java").getType());
        assertEquals(EventType.PROGRAM_RUN, factory.programRun("A", "main").getType());
        assertEquals(EventType.RUNTIME_EXCEPTION,
                factory.runtimeException("A", "NullPointerException", "x").getType());
        assertEquals(EventType.CONSOLE_OUTPUT, factory.consoleOutput(3, 40).getType());
    }

    @Test
    void renameKeepsBothNames() {
        TelemetryEvent event = factory.classRenamed("Old", "New");
        assertEquals("Old", event.getDetail("oldName"));
        assertEquals("New", event.getDetail("newName"));
    }

    @Test
    void detailsCannotBeChangedAfterCreation() {
        Map<String, Object> details = new HashMap<>();
        details.put("k", "v");
        TelemetryEvent event = new TelemetryEvent(EventType.CLASS_CREATED, Instant.now(), details);

        details.put("k", "changed");   // changing the original map...
        assertEquals("v", event.getDetail("k"));   // ...does not affect the event

        assertThrows(UnsupportedOperationException.class,
                () -> event.getDetails().put("x", 1));
    }

    @Test
    void nullDetailsBecomeEmpty() {
        TelemetryEvent event = new TelemetryEvent(EventType.CLASS_CREATED, Instant.now(), null);
        assertTrue(event.getDetails().isEmpty());
        assertNull(event.getDetail("missing"));
    }

    @Test
    void nullTypeOrTimeIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new TelemetryEvent(null, Instant.now(), null));
        assertThrows(NullPointerException.class,
                () -> new TelemetryEvent(EventType.CLASS_CREATED, null, null));
        assertThrows(NullPointerException.class, () -> new EventFactory(null));
    }

    @Test
    void toStringMentionsType() {
        assertTrue(factory.classCreated("Dog").toString().contains("CLASS_CREATED"));
    }

    @Test
    void defaultConstructorWorks() {
        assertEquals(EventType.SESSION_STARTED,
                new EventFactory().create(EventType.SESSION_STARTED).getType());
    }
}