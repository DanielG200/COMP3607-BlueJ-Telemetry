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
 * Saves a whole session as one JSON file.
 *
 * The file has three parts:
 * <pre>{@code
 * {
 *   "schemaVersion": "1.0",
 *   "session": { studentId, start and end time, totals ... },
 *   "events":  [ { "timestamp", "type", "details" }, ... ]
 * }
 * }</pre>
 * The session part is a summary at the top, so someone reading the file can see
 * the main numbers straight away without going through every event. Those
 * numbers come from {@link ReportAggregator}, the same one the PDF uses, so both
 * files always agree.
 *
 * This is one of the two strategies for {@link Exporter} (the other is
 * {@link PdfExporter}). It's made in TelemetryExtension at startup, and
 * {@link #export(TelemetrySession, Path)} is called when BlueJ closes.
 *
 * @author Kirese Tasha Maharaj
 * @see JsonWriter
 * @see ReportAggregator
 */
public class JsonExporter implements Exporter {

/**
 * The version of our JSON layout. If we ever change the layout, we'd bump this
 * so anything reading the file knows what to expect.
 */
    public static final String SCHEMA_VERSION = "1.0";

/** Works out the summary numbers for the session part of the file. */
    private final ReportAggregator aggregator;

/** Makes an exporter with its own {@link ReportAggregator} using the default settings. */
    public JsonExporter() {
        this(new ReportAggregator());
    }

/**
 * Makes an exporter that uses the aggregator you give it. TelemetryExtension
 * passes the same aggregator to the PDF exporter too, so both files use the
 * same numbers.
 *
 * @param aggregator what to use for the summary numbers; can't be {@code null}
 * @throws NullPointerException if {@code aggregator} is {@code null}
 */
    public JsonExporter(ReportAggregator aggregator) {
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator");
    }

/**
 * Saves the session as a JSON file using UTF-8.
 *
 * Side effects: creates any missing folders in the path, and replaces an
 * existing file with the same name.
 *
 * @param session the session to save
 * @param target  where to save the file
 * @throws IOException if the folders or the file can't be written
 */
    @Override
    public void export(TelemetrySession session, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, toJson(session), StandardCharsets.UTF_8);
    }

/** Returns {@code "json"}. */
    @Override
    public String fileExtension() {
        return "json";
    }

/**
 * Returns the JSON text for a session without saving it. export uses this, and
 * it's also handy in tests because we can check the text without creating a file.
 *
 * @param session the session to turn into JSON
 * @return the full JSON text for the file
 */
    public String toJson(TelemetrySession session) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("session", buildSessionMetadata(session));
        root.put("events", buildEvents(session));
        return JsonWriter.toJson(root);
    }

/**
 * Builds the session summary part of the file: the student ID, start and end
 * times, session length, active and idle time, and the totals for edits,
 * compiles, classes, methods and runs. All times ending in Ms are in
 * milliseconds.
 *
 * @param session the session to summarise
 * @return the summary as an ordered map, ready to turn into JSON
 */
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


/**
 * Builds the events part of the file: every event in the order it happened,
 * each with its timestamp, type and details.
 *
 * @param session the session whose events we want
 * @return one map per event, ready to turn into JSON
 */
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


/**
 * Rounds a number to 4 decimal places so the JSON doesn't show things like
 * 0.3333333333333. For eg, 1 out of 3 becomes 0.3333.
 *
 * @param value the number to round
 * @return the rounded number
 */
    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}