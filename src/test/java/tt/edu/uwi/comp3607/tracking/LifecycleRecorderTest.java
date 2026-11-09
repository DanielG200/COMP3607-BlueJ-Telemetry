package tt.edu.uwi.comp3607.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tt.edu.uwi.comp3607.TestClock;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.model.TelemetryEvent;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifecycleRecorderTest {

    private TelemetrySession session;
    private LifecycleRecorder recorder;
    private EventFactory factory;

    @BeforeEach
    void setUp() {
        TestClock clock = TestClock.atEpoch();
        session = new TelemetrySession("S1", clock);
        factory = new EventFactory(clock);
        recorder = new LifecycleRecorder(session, factory);
    }

    private List<TelemetryEvent> events(EventType type) {
        return session.getEventsOfType(type);
    }

    @Test
    void projectOpenedIsRecordedOnlyOnce() {
        recorder.projectOpened("/p", "Demo");
        recorder.projectOpened("/p", "Demo");

        assertEquals(1, events(EventType.PROJECT_OPENED).size());
        assertEquals("Demo", events(EventType.PROJECT_OPENED).get(0).getDetail("project"));
    }

    @Test
    void projectClosedOnlyRecordedAfterOpenAndOnlyOnce() {
        recorder.projectClosed("/p", "Demo");
        assertEquals(0, events(EventType.PROJECT_CLOSED).size());

        recorder.projectOpened("/p", "Demo");
        recorder.projectClosed("/p", "Demo");
        recorder.projectClosed("/p", "Demo");

        assertEquals(1, events(EventType.PROJECT_CLOSED).size());
    }

    @Test
    void classesPresentAtOpenAreNotReportedAsCreated() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", Set.of("run() : void"));

        recorder.classChanged("pkg", "k1", "Cat", true, Set.of("run() : void"));

        assertEquals(0, events(EventType.CLASS_CREATED).size());
        assertEquals(0, events(EventType.METHOD_ADDED).size());
    }

    @Test
    void newClassIsReportedAsCreated() {
        recorder.beginPackage("pkg");

        recorder.classChanged("pkg", "k2", "Dog", false, null);

        assertEquals(1, events(EventType.CLASS_CREATED).size());
        assertEquals("Dog", events(EventType.CLASS_CREATED).get(0).getDetail("class"));
    }

    @Test
    void changesInAnUninitialisedPackageAreIgnored() {
        recorder.classChanged("unknown", "k1", "Cat", false, null);

        assertEquals(0, session.getEvents().size());
    }

    @Test
    void editedExistingClassIsReportedAsModified() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", null);

        recorder.classChanged("pkg", "k1", "Cat", false, null);

        assertEquals(1, events(EventType.CLASS_MODIFIED).size());
        assertEquals(0, events(EventType.CLASS_CREATED).size());
    }

    @Test
    void compilingAnExistingClassIsNotAModification() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", Set.of());

        recorder.classChanged("pkg", "k1", "Cat", true, Set.of());

        assertEquals(0, events(EventType.CLASS_MODIFIED).size());
    }

    @Test
    void firstCompileOfNewClassReportsEveryMethodAsAdded() {
        recorder.beginPackage("pkg");
        recorder.classChanged("pkg", "k2", "Dog", false, null);

        recorder.classChanged("pkg", "k2", "Dog", true, Set.of("b() : int", "a() : void"));

        List<TelemetryEvent> added = events(EventType.METHOD_ADDED);
        assertEquals(2, added.size());
        assertEquals("a() : void", added.get(0).getDetail("method"));
        assertEquals("b() : int", added.get(1).getDetail("method"));
        assertEquals("Dog", added.get(0).getDetail("class"));
    }

    @Test
    void methodAddedAfterBaselineIsDetected() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", Set.of("a() : void"));

        recorder.classChanged("pkg", "k1", "Cat", true, Set.of("a() : void", "b() : int"));

        List<TelemetryEvent> added = events(EventType.METHOD_ADDED);
        assertEquals(1, added.size());
        assertEquals("b() : int", added.get(0).getDetail("method"));
    }

    @Test
    void methodRemovedIsDetected() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", Set.of("a() : void", "b() : int"));

        recorder.classChanged("pkg", "k1", "Cat", true, Set.of("a() : void"));

        List<TelemetryEvent> removed = events(EventType.METHOD_REMOVED);
        assertEquals(1, removed.size());
        assertEquals("b() : int", removed.get(0).getDetail("method"));
    }

    @Test
    void unchangedMethodsProduceNoEvents() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", Set.of("a() : void"));

        recorder.classChanged("pkg", "k1", "Cat", true, Set.of("a() : void"));
        recorder.classChanged("pkg", "k1", "Cat", true, Set.of("a() : void"));

        assertEquals(0, events(EventType.METHOD_ADDED).size());
        assertEquals(0, events(EventType.METHOD_REMOVED).size());
    }

    @Test
    void missingBaselineSnapshotMakesFirstCompileSilent() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", null);

        recorder.classChanged("pkg", "k1", "Cat", true, Set.of("a() : void"));
        assertEquals(0, events(EventType.METHOD_ADDED).size());

        recorder.classChanged("pkg", "k1", "Cat", true, Set.of("a() : void", "b() : int"));
        assertEquals(1, events(EventType.METHOD_ADDED).size());
    }

    @Test
    void renamedClassKeepsItsMethodSnapshot() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", Set.of("a() : void"));

        recorder.classRenamed("pkg", "k1", "k1b", "Cat", "Lion");
        recorder.classChanged("pkg", "k1b", "Lion", true, Set.of("a() : void"));

        List<TelemetryEvent> renamed = events(EventType.CLASS_RENAMED);
        assertEquals(1, renamed.size());
        assertEquals("Cat", renamed.get(0).getDetail("oldName"));
        assertEquals("Lion", renamed.get(0).getDetail("newName"));
        assertEquals(0, events(EventType.CLASS_CREATED).size());
        assertEquals(0, events(EventType.METHOD_ADDED).size());
    }

    @Test
    void renamedNewClassStillCountsItsMethodsAsAdded() {
        recorder.beginPackage("pkg");
        recorder.classChanged("pkg", "k2", "Dog", false, null);

        recorder.classRenamed("pkg", "k2", "k3", "Dog", "Wolf");
        recorder.classChanged("pkg", "k3", "Wolf", true, Set.of("howl() : void"));

        assertEquals(1, events(EventType.METHOD_ADDED).size());
    }

    @Test
    void removedClassIsRecordedAndForgotten() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", Set.of("a() : void"));

        recorder.classRemoved("pkg", "k1", "Cat");
        assertEquals(1, events(EventType.CLASS_REMOVED).size());
        assertEquals("Cat", events(EventType.CLASS_REMOVED).get(0).getDetail("class"));

        // Same key appears again: it is a brand new class now.
        recorder.classChanged("pkg", "k1", "Cat", true, Set.of("a() : void"));
        assertEquals(1, events(EventType.CLASS_CREATED).size());
        assertEquals(1, events(EventType.METHOD_ADDED).size());
    }

    @Test
    void closingAPackageForgetsItsClasses() {
        recorder.beginPackage("pkg");
        recorder.baselineClass("pkg", "k1", Set.of("a() : void"));

        recorder.packageClosed("pkg");
        recorder.packageClosed("never-opened");
        recorder.classChanged("pkg", "k1", "Cat", false, null);

        assertEquals(0, session.getEvents().size());
    }

    @Test
    void constructorRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new LifecycleRecorder(null, factory));
        assertThrows(NullPointerException.class, () -> new LifecycleRecorder(session, null));
    }
}
