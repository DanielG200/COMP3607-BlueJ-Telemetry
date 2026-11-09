package tt.edu.uwi.comp3607.export;

import tt.edu.uwi.comp3607.model.TelemetryEvent;
import tt.edu.uwi.comp3607.report.ReportAggregator;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Writes the whole session as one JSON file
 */
public class JsonExporter implements Exporter {

    public static final String SCHEMA_VERSION = "1.0";

    private final ReportAggregator aggregator;

    public JsonExporter() {
        this(new ReportAggregator());
    }

    public JsonExporter(ReportAggregator aggregator) {
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator");
    }

    @Override
    public void export(TelemetrySession session, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, toJson(session), StandardCharsets.UTF_8);
    }

    @Override
    public String fileExtension() {
        return "json";
    }

    /** The JSON text, so tests can check it without touching the disk. */
    public String toJson(TelemetrySession session) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("session", buildSessionMetadata(session));
        root.put("events", buildEvents(session));
        return JsonWriter.toJson(root);
    }

    private Map<String, Object> buildSessionMetadata(TelemetrySession session) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("studentId", session.getStudentId());
        meta.put("sessionStart", session.getStartTime());
        meta.put("sessionEnd", session.getEndTime());
        meta.put("sessionDurationMs", session.getDuration().toMillis());
        meta.put("activeCodingTimeMs", aggregator.activeCodingTime(session).toMillis());
        meta.put("idleTimeMs", aggregator.idleTime(session).toMillis());
        meta.put("totalEvents", session.getEvents().size());
        meta.put("totalEdits", aggregator.totalEdits(session));
        meta.put("totalCompilationAttempts", aggregator.totalCompilationAttempts(session));
        meta.put("successfulCompilations", aggregator.successfulCompilations(session));
        meta.put("failedCompilations", aggregator.failedCompilations(session));
        meta.put("compileSuccessRate", round(aggregator.compileSuccessRate(session)));
        meta.put("totalCompileErrors", aggregator.totalCompileErrors(session));
        meta.put("classesCreated", aggregator.classesCreated(session));
        meta.put("methodsAdded", aggregator.methodsAdded(session));
        meta.put("programRuns", aggregator.programRuns(session));
        meta.put("runtimeExceptions", aggregator.runtimeExceptions(session));
        return meta;
    }

    private List<Object> buildEvents(TelemetrySession session) {
        List<Object> list = new ArrayList<>();
        for (TelemetryEvent event : session.getEvents()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", event.getTimestamp());
            entry.put("type", event.getType());
            entry.put("details", event.getDetails());
            list.add(entry);
        }
        return list;
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}