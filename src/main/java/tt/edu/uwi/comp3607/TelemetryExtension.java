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

public class TelemetryExtension extends Extension {

    /** Where the exported JSON / PDF files go. Works on Windows and Mac. */
    static final Path OUTPUT_DIR = Paths.get(
            System.getProperty("user.home"), "COMP3607Telemetry");

    private TelemetrySession session;
    private EventFactory eventFactory;
    private EditorActivityListener editorActivityListener;

    /** Every output format (JSON and PDF). */
    private final List<Exporter> exporters = new ArrayList<>();

    @Override
    public boolean isCompatible() {
        return true;
    }

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

    @Override
    public String getName() {
        return "COMP3607 Telemetry";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "Collects BlueJ project, editor, compilation and execution telemetry.";
    }
}