
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EditorActivityListener implements AutoCloseable {

    private final BlueJ bluej;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "Telemetry-Editor-Monitor");
                thread.setDaemon(true);
                return thread;
            });

    private final AtomicBoolean scanQueued = new AtomicBoolean(false);

    private final Map<String, EditorRegistration> registrations =
            new HashMap<>();

    private final Map<String, Long> lastEditTimes =
            new HashMap<>();

    private final Path logFile = Paths.get(
            System.getProperty("user.home"),
            "Library",
            "Logs",
            "COMP3607Telemetry",
            "editor_activity.jsonl"
    );

    private volatile boolean running = false;

    public EditorActivityListener(BlueJ bluej) {
        this.bluej = bluej;
    }

    /**
     * Starts periodic editor discovery.
     */
    public void start() {

        if (running) {
            return;
        }

        running = true;

        writeEvent(
                "{\"timestamp\":\"" + Instant.now()
                        + "\",\"event\":\"EDITOR_MONITOR_STARTED\"}"
        );

        scheduler.scheduleWithFixedDelay(
                this::requestEditorScan,
                0,
                1,
                TimeUnit.SECONDS
        );
    }

    /**
     * Requests an editor scan on the JavaFX application thread.
     */
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

                    System.err.println(
                            "[Telemetry] Editor scan failed: "
                                    + exception.getMessage()
                    );

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
     *
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

                            String classKey = createClassKey(
                                    project,
                                    currentPackage,
                                    currentClass
                            );

                            discoveredClasses.add(classKey);

                            JavaEditor javaEditor =
                                    currentClass.getJavaEditorIfOpen();

                            if (javaEditor == null) {
                                continue;
                            }

                            Editor underlyingEditor =
                                    EditorBridge.getJavaEditor(javaEditor);

                            if (underlyingEditor == null) {
                                continue;
                            }

                            registerEditor(
                                    classKey,
                                    currentClass.getName(),
                                    javaEditor,
                                    underlyingEditor
                            );

                        } catch (Exception exception) {

                            System.err.println(
                                    "[Telemetry] Could not inspect class: "
                                            + exception.getMessage()
                            );

                        }
                    }
                }

            } catch (Exception exception) {

                System.err.println(
                        "[Telemetry] Could not inspect project: "
                                + exception.getMessage()
                );

            }
        }

        removeDeletedClasses(discoveredClasses);
    }

    /**
     * Creates a stable identifier for a class.
     */
    private String createClassKey(
            BProject project,
            BPackage currentPackage,
            BClass currentClass
    ) throws Exception {

        return project.getDir()
                .getAbsolutePath()
                + "|"
                + currentPackage.getName()
                + "|"
                + currentClass.getName();
    }

    /**
     * Attaches one document listener to each underlying editor.
     */
    private void registerEditor(
            String classKey,
            String className,
            JavaEditor javaEditor,
            Editor underlyingEditor
    ) {

        EditorRegistration existing = registrations.get(classKey);

        // The same underlying editor is already being monitored.
        if (existing != null
                && existing.underlyingEditor == underlyingEditor) {
            return;
        }

        // Remove the previous listener if this is a new editor instance.
        if (existing != null) {

            try {
                existing.javaEditor.removeDocumentListener(
                        existing.documentListener
                );
            } catch (Exception ignored) {
                // The previous editor may already have closed.
            }
        }

        DocumentListener documentListener =
                (position, oldText, newText, valueOne, valueTwo) -> {

                    int removedCharacters =
                            oldText == null ? 0 : oldText.length();

                    int insertedCharacters =
                            newText == null ? 0 : newText.length();

                    recordEdit(
                            classKey,
                            className,
                            removedCharacters,
                            insertedCharacters
                    );
                };

        javaEditor.addDocumentListener(documentListener);

        registrations.put(
                classKey,
                new EditorRegistration(
                        javaEditor,
                        underlyingEditor,
                        documentListener
                )
        );

        writeEvent(
                "{\"timestamp\":\"" + Instant.now()
                        + "\",\"event\":\"EDITOR_ATTACHED\""
                        + ",\"class\":\"" + escapeJson(className) + "\"}"
        );

    }

    /**
     * Records an individual document-edit operation.
     *
     * Actual source code is never recorded.
     */
    private synchronized void recordEdit(
            String classKey,
            String className,
            int removedCharacters,
            int insertedCharacters
    ) {

        long currentTime = System.currentTimeMillis();

        Long previousTime = lastEditTimes.put(
                classKey,
                currentTime
        );

        long gapSincePreviousEdit = -1;

        if (previousTime != null) {

            gapSincePreviousEdit =
                    currentTime - previousTime;

        }

        String event = "{"
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"event\":\"DOCUMENT_EDIT\","
                + "\"class\":\"" + escapeJson(className) + "\","
                + "\"removedCharacters\":" + removedCharacters + ","
                + "\"insertedCharacters\":" + insertedCharacters + ","
                + "\"gapSincePreviousEditMs\":" + gapSincePreviousEdit
                + "}";

        writeEvent(event);
    }

    /**
     * Removes registrations for classes no longer in open projects.
     */
    private void removeDeletedClasses(Set<String> discoveredClasses) {

        Iterator<Map.Entry<String, EditorRegistration>> iterator =
                registrations.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<String, EditorRegistration> entry =
                    iterator.next();

            if (!discoveredClasses.contains(entry.getKey())) {

                EditorRegistration registration = entry.getValue();

                try {

                    registration.javaEditor.removeDocumentListener(
                            registration.documentListener
                    );

                } catch (Exception ignored) {
                    // The editor may already have been disposed.
                }

                iterator.remove();
                lastEditTimes.remove(entry.getKey());
            }
        }
    }

    private synchronized void writeEvent(String event) {

        try {

            Files.createDirectories(logFile.getParent());

            Files.write(
                    logFile,
                    (event + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (IOException exception) {

            System.err.println(
                    "[Telemetry] Could not write editor activity: "
                            + exception.getMessage()
            );

        }
    }

    private String escapeJson(String value) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Stops monitoring and removes registered document listeners.
     */
    @Override
    public void close() {

        running = false;

        scheduler.shutdownNow();

        Runnable cleanup = () -> {

            for (EditorRegistration registration : registrations.values()) {

                try {

                    registration.javaEditor.removeDocumentListener(
                            registration.documentListener
                    );

                } catch (Exception ignored) {
                    // Editor may already be closed.
                }
            }

            registrations.clear();
            lastEditTimes.clear();

            writeEvent(
                    "{\"timestamp\":\"" + Instant.now()
                            + "\",\"event\":\"EDITOR_MONITOR_STOPPED\"}"
            );
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

    private static class EditorRegistration {

        private final JavaEditor javaEditor;
        private final Editor underlyingEditor;
        private final DocumentListener documentListener;

        private EditorRegistration(
                JavaEditor javaEditor,
                Editor underlyingEditor,
                DocumentListener documentListener
        ) {

            this.javaEditor = javaEditor;
            this.underlyingEditor = underlyingEditor;
            this.documentListener = documentListener;
        }
    }
}
