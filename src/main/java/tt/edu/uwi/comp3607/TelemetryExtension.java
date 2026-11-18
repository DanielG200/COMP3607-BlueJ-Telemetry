package tt.edu.uwi.comp3607;

import bluej.extensions2.BlueJ;
import bluej.extensions2.Extension;
import tt.edu.uwi.comp3607.export.Exporter;
import tt.edu.uwi.comp3607.export.JsonExporter;
import tt.edu.uwi.comp3607.export.PdfExporter;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.report.ReportAggregator;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the plugin. BlueJ loads this class from the jar. It creates the session, registers
 * the listeners at startup, and at shutdown ends the session and writes the JSON and PDF files.
 *
 * @author Daniel Goodridge
 * @author Kirese Tasha Maharaj
 * @author Raymond Sookdeo
 */
public class TelemetryExtension extends Extension {

    /** Where the exported JSON / PDF files go. Works on Windows and Mac. */
    static final Path OUTPUT_DIR = Paths.get(
            System.getProperty("user.home"), "COMP3607Telemetry");

    /** The one session that collects every event until BlueJ closes. */
    private TelemetrySession session;
    /** Creates every event with its timestamp. */
    private EventFactory eventFactory;
    /** Kept so it can be stopped when BlueJ closes. */
    private EditorActivityListener editorActivityListener;

    /** Every output format (JSON and PDF). */
    private final List<Exporter> exporters = new ArrayList<>();

    /** Tells BlueJ this extension works with any version. */
    @Override
    public boolean isCompatible() {
        return true;
    }

    /** Called by BlueJ when it starts. Creates the session and exporters and registers every listener. */
    @Override
    public void startup(BlueJ bluej) {

        session = new TelemetrySession();
        eventFactory = new EventFactory();
        session.record(eventFactory.create(EventType.SESSION_STARTED));

        ReportAggregator aggregator = new ReportAggregator();
        exporters.add(new JsonExporter(aggregator));
        exporters.add(new PdfExporter(aggregator));

        // Compilation telemetry
        bluej.addCompileListener(new CompilationListener(session, eventFactory));

        // Project and class lifecycle telemetry
        LifecycleListener lifecycleListener = new LifecycleListener(session, eventFactory);
        bluej.addPackageListener(lifecycleListener);
        bluej.addClassListener(lifecycleListener);

        // Execution telemetry (program runs, runtime exceptions)
        bluej.addInvocationFinishedListener(new ExecutionListener(session, eventFactory));

        // Editor telemetry
        editorActivityListener = new EditorActivityListener(bluej, session, eventFactory);
        editorActivityListener.start();

        System.out.println("COMP3607 Telemetry Extension Loaded");
    }

    /** Called by BlueJ when it closes: end the session and write the files. */
    @Override
    public void terminate() {

        if (editorActivityListener != null) {
            editorActivityListener.close();
        }

        if (session == null) {
            return;
        }

        session.record(eventFactory.create(EventType.SESSION_ENDED));
        session.end();
        exportAll();
    }

    /** Writes the session with every exporter. Also usable for a manual "Export" menu item. */
    void exportAll() {
        String baseName = "session-" + Instant.now().toString().replace(':', '-');

        for (Exporter exporter : exporters) {
            Path target = OUTPUT_DIR.resolve(baseName + "." + exporter.fileExtension());
            try {
                exporter.export(session, target);
                System.out.println("[Telemetry] Exported " + target);
            } catch (IOException | RuntimeException exception) {
                System.err.println("[Telemetry] Export failed for "
                        + exporter.fileExtension() + ": " + exception.getMessage());
            }
        }
    }

    /** Returns the name BlueJ shows in Help, Installed Extensions. */
    @Override
    public String getName() {
        return "COMP3607 Telemetry";
    }

    /** Returns the plugin version shown in Help, Installed Extensions. */
    @Override
    public String getVersion() {
        return "1.0";
    }

    /** Returns the one line description BlueJ shows for this extension. */
    @Override
    public String getDescription() {
        return "Collects BlueJ project, editor, compilation and execution telemetry.";
    }
}