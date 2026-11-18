package tt.edu.uwi.comp3607;

import bluej.editor.Editor;
import bluej.extensions2.BClass;
import bluej.extensions2.BPackage;
import bluej.extensions2.BProject;
import bluej.extensions2.BlueJ;
import bluej.extensions2.SourceType;
import bluej.extensions2.editor.DocumentListener;
import bluej.extensions2.editor.EditorBridge;
import bluej.extensions2.editor.JavaEditor;
import javafx.application.Platform;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.session.TelemetrySession;
import tt.edu.uwi.comp3607.tracking.EditRecorder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BlueJ adapter for editor telemetry. It finds open editors and attaches a
 * document listener to each. The edits go to EditRecorder, which records
 * them in the shared TelemetrySession. Source code is never recorded.
 */
public class EditorActivityListener implements AutoCloseable {

    /** Used to find the open projects and their editors. */
    private final BlueJ bluej;
    /** Turns each edit into an event. */
    private final EditRecorder recorder;

    /** Background thread that asks for an editor scan every second. */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "Telemetry-Editor-Monitor");
                thread.setDaemon(true);
                return thread;
            });

    /** True while a scan is waiting for the JavaFX thread, so scans never pile up. */
    private final AtomicBoolean scanQueued = new AtomicBoolean(false);

    /** The editor and listener currently attached for each class, by class key. */
    private final Map<String, EditorRegistration> registrations = new HashMap<>();

    /** True between {@link #start()} and {@link #close()}. */
    private volatile boolean running = false;

    /**
     * Creates a listener. Nothing is watched until {@link #start()} is called.
     *
     * @param bluej        the BlueJ instance, used to find open editors
     * @param session      session that receives the edit events
     * @param eventFactory factory used to build the events
     */
    public EditorActivityListener(BlueJ bluej, TelemetrySession session, EventFactory eventFactory) {
        this.bluej = bluej;
        this.recorder = new EditRecorder(session, eventFactory);
    }

    /** Starts checking for newly opened editors once a second. Does nothing if already started. */
    public void start() {
        if (running) {
            return;
        }
        running = true;

        scheduler.scheduleWithFixedDelay(
                this::requestEditorScan,
                0,
                1,
                TimeUnit.SECONDS
        );
    }

    /** Requests an editor scan on the JavaFX application thread. */
    private void requestEditorScan() {
        if (!running || !scanQueued.compareAndSet(false, true)) {
            return;
        }

        try {
            Platform.runLater(() -> {
                try {
                    if (running) {
                        scanEditors();
                    }
                } catch (Exception exception) {
                    System.err.println("[Telemetry] Editor scan failed: " + exception.getMessage());
                } finally {
                    scanQueued.set(false);
                }
            });
        } catch (IllegalStateException exception) {
            scanQueued.set(false);
        }
    }

    /**
     * Searches all open BlueJ projects for open Java editors.
     * This method runs on the JavaFX application thread.
     */
    private void scanEditors() {
        Set<String> discoveredClasses = new HashSet<>();

        BProject[] projects = bluej.getOpenProjects();

        for (BProject project : projects) {
            try {
                BPackage[] packages = project.getPackages();

                for (BPackage currentPackage : packages) {
                    BClass[] classes = currentPackage.getClasses();

                    for (BClass currentClass : classes) {
                        try {
                            if (currentClass.getSourceType() != SourceType.Java) {
                                continue;
                            }

                            String classKey = createClassKey(project, currentPackage, currentClass);
                            discoveredClasses.add(classKey);

                            JavaEditor javaEditor = currentClass.getJavaEditorIfOpen();
                            if (javaEditor == null) {
                                continue;
                            }

                            Editor underlyingEditor = EditorBridge.getJavaEditor(javaEditor);
                            if (underlyingEditor == null) {
                                continue;
                            }

                            registerEditor(classKey, currentClass.getName(), javaEditor, underlyingEditor);

                        } catch (Exception exception) {
                            System.err.println("[Telemetry] Could not inspect class: " + exception.getMessage());
                        }
                    }
                }
            } catch (Exception exception) {
                System.err.println("[Telemetry] Could not inspect project: " + exception.getMessage());
            }
        }

        removeDeletedClasses(discoveredClasses);
    }

    /** Returns a key that identifies a class by its project folder, package and name. */
    private String createClassKey(BProject project, BPackage currentPackage, BClass currentClass)
            throws Exception {
        return project.getDir().getAbsolutePath()
                + "|"
                + currentPackage.getName()
                + "|"
                + currentClass.getName();
    }

    /**
     * Attaches a document listener to the editor if it is not already watched. If the class has a
     * different editor now, the old listener is removed first.
     */
    private void registerEditor(String classKey, String className,
                                JavaEditor javaEditor, Editor underlyingEditor) {

        EditorRegistration existing = registrations.get(classKey);

        // The same underlying editor is already being monitored.
        if (existing != null && existing.underlyingEditor == underlyingEditor) {
            return;
        }

        // Remove the previous listener if this is a new editor instance.
        if (existing != null) {
            try {
                existing.javaEditor.removeDocumentListener(existing.documentListener);
            } catch (Exception ignored) {
                // The previous editor may already have closed.
            }
        }

        DocumentListener documentListener =
                (position, oldText, newText, valueOne, valueTwo) -> {
                    int removedCharacters = oldText == null ? 0 : oldText.length();
                    int insertedCharacters = newText == null ? 0 : newText.length();

                    recorder.recordEdit(
                            classKey,
                            className,
                            insertedCharacters,
                            removedCharacters,
                            System.currentTimeMillis());
                };

        javaEditor.addDocumentListener(documentListener);

        registrations.put(
                classKey,
                new EditorRegistration(javaEditor, underlyingEditor, documentListener));
    }

    /** Removes registrations for classes no longer in open projects. */
    private void removeDeletedClasses(Set<String> discoveredClasses) {
        Iterator<Map.Entry<String, EditorRegistration>> iterator =
                registrations.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, EditorRegistration> entry = iterator.next();

            if (!discoveredClasses.contains(entry.getKey())) {
                EditorRegistration registration = entry.getValue();
                try {
                    registration.javaEditor.removeDocumentListener(registration.documentListener);
                } catch (Exception ignored) {
                    // The editor may already have been disposed.
                }
                iterator.remove();
                recorder.forget(entry.getKey());
            }
        }
    }

    /** Stops monitoring and removes registered document listeners. */
    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();

        Runnable cleanup = () -> {
            for (EditorRegistration registration : registrations.values()) {
                try {
                    registration.javaEditor.removeDocumentListener(registration.documentListener);
                } catch (Exception ignored) {
                    // Editor may already be closed.
                }
            }
            registrations.clear();
            recorder.clear();
        };

        if (Platform.isFxApplicationThread()) {
            cleanup.run();
        } else {
            try {
                Platform.runLater(cleanup);
            } catch (IllegalStateException ignored) {
                // JavaFX may already be shutting down.
            }
        }
    }

    /** Remembers which editor and listener belong together, so the listener can be removed later. */
    private static class EditorRegistration {

        private final JavaEditor javaEditor;
        private final Editor underlyingEditor;
        private final DocumentListener documentListener;

        private EditorRegistration(JavaEditor javaEditor, Editor underlyingEditor,
                                   DocumentListener documentListener) {
            this.javaEditor = javaEditor;
            this.underlyingEditor = underlyingEditor;
            this.documentListener = documentListener;
        }
    }
}
