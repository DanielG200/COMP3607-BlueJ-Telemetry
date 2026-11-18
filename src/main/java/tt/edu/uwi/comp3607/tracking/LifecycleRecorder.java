package tt.edu.uwi.comp3607.tracking;

import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns "what BlueJ told us" about projects, classes and methods into
 * telemetry events.
 *
 * This class knows nothing about BlueJ. LifecycleListener reads the BlueJ
 * objects and passes plain strings here, so all the decision making
 * (is this class new? which methods were added?) can be unit tested.
 */
public class LifecycleRecorder {

    private final TelemetrySession session;
    private final EventFactory eventFactory;

    /** Projects we have already reported as opened. */
    private final Set<String> openProjects = new HashSet<>();

    /** Per package: the classes we already know about. A package is "initialised" once it has an entry. */
    private final Map<String, Set<String>> knownClasses = new HashMap<>();

    /** Per class: the method signatures seen at the last compile. */
    private final Map<String, Set<String>> methodSnapshots = new HashMap<>();

    /** Classes created during this session (not present when the project opened). */
    private final Set<String> createdInSession = new HashSet<>();

    /**
     * Creates a recorder that writes its events to the given session.
     *
     * @param session      session that receives the events; must not be null
     * @param eventFactory factory used to build the events; must not be null
     * @throws NullPointerException if either argument is null
     */
    public LifecycleRecorder(TelemetrySession session, EventFactory eventFactory) {
        this.session = Objects.requireNonNull(session, "session");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory");
    }

    // ---------- Projects and packages ----------

    /** Records PROJECT_OPENED the first time a project is seen. */
    public synchronized void projectOpened(String projectKey, String projectName) {
        if (openProjects.add(projectKey)) {
            session.record(eventFactory.projectOpened(projectName));
        }
    }

    /** Records PROJECT_CLOSED once, if the project was reported as opened. */
    public synchronized void projectClosed(String projectKey, String projectName) {
        if (openProjects.remove(projectKey)) {
            session.record(eventFactory.projectClosed(projectName));
        }
    }

    /** Starts tracking a package. Classes already in it are NOT reported as created. */
    public synchronized void beginPackage(String packageKey) {
        knownClasses.putIfAbsent(packageKey, new HashSet<>());
    }

    /**
     * Registers a class that already existed when the package opened.
     * No events are recorded. The method snapshot (may be null if BlueJ could
     * not provide one) becomes the baseline for later comparisons.
     */
    public synchronized void baselineClass(String packageKey, String classKey, Set<String> methods) {
        knownClasses.computeIfAbsent(packageKey, key -> new HashSet<>()).add(classKey);
        if (methods != null) {
            methodSnapshots.put(classKey, new TreeSet<>(methods));
        }
    }

    /** Forgets everything about a package (called when it closes). */
    public synchronized void packageClosed(String packageKey) {
        Set<String> classes = knownClasses.remove(packageKey);
        if (classes != null) {
            for (String classKey : classes) {
                methodSnapshots.remove(classKey);
                createdInSession.remove(classKey);
            }
        }
    }

    // ---------- Classes ----------

    /**
     * Called whenever BlueJ reports a class state change. Records CLASS_CREATED for a class we have
     * not seen in this package, CLASS_MODIFIED when a known class stops being compiled (it was edited),
     * and method changes when the class compiles. Ignored for packages that were never started.
     *
     * @param packageKey     key of the package the class belongs to
     * @param classKey       key that stays the same for the life of the class
     * @param className      class name written into the events
     * @param compiled       whether the class is compiled right now
     * @param currentMethods method signatures when compiled, otherwise null
     */
    public synchronized void classChanged(String packageKey, String classKey, String className,
                                          boolean compiled, Set<String> currentMethods) {
        Set<String> known = knownClasses.get(packageKey);

        if (known != null) {
            if (known.add(classKey)) {
                createdInSession.add(classKey);
                session.record(eventFactory.classCreated(className));
            } else if (!compiled) {
                // BlueJ marks a class "not compiled" when its source is edited.
                session.record(eventFactory.classModified(className));
            }
        }

        if (compiled && currentMethods != null) {
            compareMethods(classKey, className, currentMethods);
        }
    }

    /**
     * Records CLASS_RENAMED and moves everything we know about the class to its new key, so a rename
     * is not mistaken for a new class or for added and removed methods.
     *
     * @param packageKey key of the package the class belongs to
     * @param oldKey     key of the class before the rename
     * @param newKey     key of the class after the rename
     * @param oldName    name before the rename
     * @param newName    name after the rename
     */
    public synchronized void classRenamed(String packageKey, String oldKey, String newKey,
                                          String oldName, String newName) {
        session.record(eventFactory.classRenamed(oldName, newName));

        Set<String> known = knownClasses.get(packageKey);
        if (known != null) {
            known.remove(oldKey);
            known.add(newKey);
        }
        Set<String> snapshot = methodSnapshots.remove(oldKey);
        if (snapshot != null) {
            methodSnapshots.put(newKey, snapshot);
        }
        if (createdInSession.remove(oldKey)) {
            createdInSession.add(newKey);
        }
    }

    /**
     * Records CLASS_REMOVED and forgets the class. If the same key appears again later, it is
     * treated as a brand new class.
     *
     * @param packageKey key of the package the class belonged to
     * @param classKey   key of the removed class
     * @param className  class name written into the event
     */
    public synchronized void classRemoved(String packageKey, String classKey, String className) {
        session.record(eventFactory.classRemoved(className));

        Set<String> known = knownClasses.get(packageKey);
        if (known != null) {
            known.remove(classKey);
        }
        methodSnapshots.remove(classKey);
        createdInSession.remove(classKey);
    }

    // ---------- Methods ----------

    /**
     * Compares the current methods with the snapshot from the last compile and records
     * METHOD_ADDED and METHOD_REMOVED events. The first look at a class that was already there only
     * saves a baseline and records nothing.
     */
    private void compareMethods(String classKey, String className, Set<String> current) {
        Set<String> previous = methodSnapshots.get(classKey);

        if (previous == null && !createdInSession.contains(classKey)) {
            // First look at a class that was already there: this is a baseline, not new work.
            methodSnapshots.put(classKey, new TreeSet<>(current));
            return;
        }
        if (previous == null) {
            previous = new TreeSet<>();
        }

        for (String signature : new TreeSet<>(current)) {
            if (!previous.contains(signature)) {
                session.record(eventFactory.methodAdded(className, signature));
            }
        }
        for (String signature : previous) {
            if (!current.contains(signature)) {
                session.record(eventFactory.methodRemoved(className, signature));
            }
        }
        methodSnapshots.put(classKey, new TreeSet<>(current));
    }
}
