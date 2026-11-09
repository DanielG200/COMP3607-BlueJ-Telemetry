package tt.edu.uwi.comp3607.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tt.edu.uwi.comp3607.TestClock;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.model.TelemetryEvent;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EditRecorderTest {

    private TelemetrySession session;
    private EventFactory factory;
    private EditRecorder recorder;

    @BeforeEach
    void setUp() {
        TestClock clock = TestClock.atEpoch();
        session = new TelemetrySession("S1", clock);
        factory = new EventFactory(clock);
        recorder = new EditRecorder(session, factory);
    }

    private List<TelemetryEvent> edits() {
        return session.getEventsOfType(EventType.DOCUMENT_EDIT);
    }

    @Test
    void firstEditHasNoPreviousGapAndKeepsCounts() {
        recorder.recordEdit("k1", "Cat", 5, 2, 1000);

        TelemetryEvent edit = edits().get(0);
        assertEquals("Cat", edit.getDetail("class"));
        assertEquals(5, edit.getDetail("insertedCharacters"));
        assertEquals(2, edit.getDetail("removedCharacters"));
        assertEquals(EditRecorder.NO_PREVIOUS_EDIT, edit.getDetail("gapSincePreviousEditMs"));
    }

    @Test
    void gapIsMeasuredSeparatelyForEachClass() {
        recorder.recordEdit("k1", "Cat", 1, 0, 1000);
        recorder.recordEdit("k2", "Dog", 1, 0, 1500);
        recorder.recordEdit("k1", "Cat", 1, 0, 4000);

        assertEquals(3, edits().size());
        assertEquals(-1L, edits().get(1).getDetail("gapSincePreviousEditMs"));
        assertEquals(3000L, edits().get(2).getDetail("gapSincePreviousEditMs"));
    }

    @Test
    void forgetResetsTheGapForThatClassOnly() {
        recorder.recordEdit("k1", "Cat", 1, 0, 1000);
        recorder.recordEdit("k2", "Dog", 1, 0, 1000);

        recorder.forget("k1");
        recorder.recordEdit("k1", "Cat", 1, 0, 2000);
        recorder.recordEdit("k2", "Dog", 1, 0, 2000);

        assertEquals(-1L, edits().get(2).getDetail("gapSincePreviousEditMs"));
        assertEquals(1000L, edits().get(3).getDetail("gapSincePreviousEditMs"));
    }

    @Test
    void clearResetsEveryClass() {
        recorder.recordEdit("k1", "Cat", 1, 0, 1000);
        recorder.recordEdit("k2", "Dog", 1, 0, 1000);

        recorder.clear();
        recorder.recordEdit("k1", "Cat", 1, 0, 2000);
        recorder.recordEdit("k2", "Dog", 1, 0, 2000);

        assertEquals(-1L, edits().get(2).getDetail("gapSincePreviousEditMs"));
        assertEquals(-1L, edits().get(3).getDetail("gapSincePreviousEditMs"));
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new EditRecorder(null, factory));
        assertThrows(NullPointerException.class, () -> new EditRecorder(session, null));
    }
}
