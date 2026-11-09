package tt.edu.uwi.comp3607.export;

import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.model.TelemetryEvent;
import tt.edu.uwi.comp3607.report.ReportAggregator;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * DESIGN PATTERN: Strategy (the PDF version of Exporter).
 *
 * Builds the end-of-session report. All the numbers come from
 * ReportAggregator, and all the PDF drawing is done by PdfWriter.
 * This class only decides the layout: which sections appear and in what order.
 *
 * Sections: session overview, active vs idle time, compilation summary and
 * error frequency, execution summary, and the class/method progression timeline.
 */
public class PdfExporter implements Exporter {

    private static final float MARGIN = 50f;
    private static final float CONTENT_WIDTH = PdfWriter.PAGE_WIDTH - 2 * MARGIN;
    private static final float TOP = PdfWriter.PAGE_HEIGHT - MARGIN;
    private static final float BOTTOM = 65f;

    private static final float[] INK = {0.13f, 0.15f, 0.18f};
    private static final float[] MUTED = {0.42f, 0.45f, 0.50f};
    private static final float[] ACCENT = {0.15f, 0.35f, 0.65f};
    private static final float[] GOOD = {0.18f, 0.58f, 0.36f};
    private static final float[] BAD = {0.78f, 0.25f, 0.22f};
    private static final float[] IDLE = {0.93f, 0.62f, 0.20f};
    private static final float[] SHADE = {0.94f, 0.95f, 0.97f};
    private static final float[] RULE = {0.80f, 0.82f, 0.86f};
    private static final float[] WHITE = {1f, 1f, 1f};

    private static final int MAX_ERRORS_IN_CHART = 10;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final ReportAggregator aggregator;
    private final ZoneId zone;

    public PdfExporter() {
        this(new ReportAggregator());
    }

    public PdfExporter(ReportAggregator aggregator) {
        this(aggregator, ZoneId.systemDefault());
    }

    public PdfExporter(ReportAggregator aggregator, ZoneId zone) {
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    public void export(TelemetrySession session, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, toBytes(session));
    }

    @Override
    public String fileExtension() {
        return "pdf";
    }

    /** The PDF file content, so tests can check it without touching the disk. */
    public byte[] toBytes(TelemetrySession session) {
        Objects.requireNonNull(session, "session");
        return new ReportBuilder(session).build();
    }

    // ---------- Formatting helpers (package-private so tests can reach them) ----------

    /** 3725 seconds becomes "1h 02m 05s". */
    static String formatDuration(Duration duration) {
        long totalSeconds = Math.max(0, duration.getSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
        }
        return seconds + "s";
    }

    static String formatPercent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }

    /** Time since the session started, like "+12:05". */
    static String formatOffset(Duration offset) {
        long totalSeconds = Math.max(0, offset.getSeconds());
        return String.format(Locale.ROOT, "+%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    // ---------- Layout ----------

    /** Holds the drawing position while one report is being built. */
    private final class ReportBuilder {

        private final TelemetrySession session;
        private final PdfWriter pdf = new PdfWriter();
        private float y = TOP;

        ReportBuilder(TelemetrySession session) {
            this.session = session;
        }

        byte[] build() {
            drawTitle();
            drawOverview();
            drawActiveVersusIdle();
            drawCompilation();
            drawExecution();
            drawTimeline();
            drawFooters();
            return pdf.toBytes();
        }

        // ----- Sections -----

        private void drawTitle() {
            pdf.fillRect(0, PdfWriter.PAGE_HEIGHT - 88, PdfWriter.PAGE_WIDTH, 88, ACCENT);
            pdf.text(MARGIN, PdfWriter.PAGE_HEIGHT - 45, 22, true, WHITE, "BlueJ Telemetry Report");
            pdf.text(MARGIN, PdfWriter.PAGE_HEIGHT - 66, 10, false, WHITE,
                    "Student ID: " + session.getStudentId() + "   |   Session start: "
                            + TIME_FORMAT.format(session.getStartTime().atZone(zone)));
            y = PdfWriter.PAGE_HEIGHT - 88 - 14;
        }

        private void drawOverview() {
            section("Session overview");
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Session start", TIME_FORMAT.format(session.getStartTime().atZone(zone))});
            rows.add(new String[]{"Session end", session.getEndTime() == null
                    ? "Still running" : TIME_FORMAT.format(session.getEndTime().atZone(zone))});
            rows.add(new String[]{"Total session duration", formatDuration(session.getDuration())});
            rows.add(new String[]{"Total events recorded", String.valueOf(session.getEvents().size())});
            rows.add(new String[]{"Total edits", String.valueOf(aggregator.totalEdits(session))});
            rows.add(new String[]{"Edits per active minute",
                    String.format(Locale.ROOT, "%.1f", aggregator.editsPerActiveMinute(session))});
            rows.add(new String[]{"Classes created", String.valueOf(aggregator.classesCreated(session))});
            rows.add(new String[]{"Methods defined", String.valueOf(aggregator.methodsAdded(session))});
            table(new String[]{"Measure", "Value"}, new float[]{300, CONTENT_WIDTH - 300}, rows);
        }

        private void drawActiveVersusIdle() {
            section("Active coding time vs idle time");
            Duration active = aggregator.activeCodingTime(session);
            Duration idle = aggregator.idleTime(session);
            long total = active.toMillis() + idle.toMillis();

            if (total <= 0) {
                note("No session time was recorded.");
                return;
            }

            double activeShare = (double) active.toMillis() / total;
            ensure(60);
            float barY = y - 20;
            float activeWidth = (float) (CONTENT_WIDTH * activeShare);
            pdf.fillRect(MARGIN, barY, activeWidth, 20, GOOD);
            pdf.fillRect(MARGIN + activeWidth, barY, CONTENT_WIDTH - activeWidth, 20, IDLE);
            y = barY - 16;

            legend(MARGIN, GOOD, "Active: " + formatDuration(active) + " (" + formatPercent(activeShare) + ")");
            legend(MARGIN + 250, IDLE, "Idle: " + formatDuration(idle) + " (" + formatPercent(1 - activeShare) + ")");
            y -= 16;
            note("Active time adds up the gaps between edits that are 60 seconds or shorter. Longer gaps count as idle.");
        }

        private void drawCompilation() {
            section("Compilation");
            int attempts = aggregator.totalCompilationAttempts(session);

            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Compilation attempts", String.valueOf(attempts)});
            rows.add(new String[]{"Successful", String.valueOf(aggregator.successfulCompilations(session))});
            rows.add(new String[]{"Failed", String.valueOf(aggregator.failedCompilations(session))});
            rows.add(new String[]{"Success rate", formatPercent(aggregator.compileSuccessRate(session))});
            rows.add(new String[]{"Error ratio (failed / attempts)", formatPercent(aggregator.compileErrorRatio(session))});
            rows.add(new String[]{"Compile errors reported", String.valueOf(aggregator.totalCompileErrors(session))});
            table(new String[]{"Measure", "Value"}, new float[]{300, CONTENT_WIDTH - 300}, rows);

            subheading("Compilation error frequency");
            Map<String, Integer> frequency = aggregator.compileErrorFrequency(session);
            if (frequency.isEmpty()) {
                note("No compilation errors were recorded.");
                return;
            }
            barChart(frequency);
        }

        private void drawExecution() {
            section("Program execution");
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Program runs", String.valueOf(aggregator.programRuns(session))});
            rows.add(new String[]{"Runtime exceptions", String.valueOf(aggregator.runtimeExceptions(session))});
            rows.add(new String[]{"Console output summaries",
                    String.valueOf(session.getEventsOfType(EventType.CONSOLE_OUTPUT).size())});
            table(new String[]{"Measure", "Value"}, new float[]{300, CONTENT_WIDTH - 300}, rows);
        }

        private void drawTimeline() {
            section("Progression timeline: classes and methods");
            List<TelemetryEvent> timeline = aggregator.progressionTimeline(session);
            if (timeline.isEmpty()) {
                note("No classes or methods were created in this session.");
                return;
            }

            List<String[]> rows = new ArrayList<>();
            for (TelemetryEvent event : timeline) {
                boolean isClass = event.getType() == EventType.CLASS_CREATED;
                Duration offset = Duration.between(session.getStartTime(), event.getTimestamp());
                String className = String.valueOf(event.getDetail("class"));
                String name = isClass ? className : className + "." + event.getDetail("method");
                rows.add(new String[]{
                        formatOffset(offset),
                        TIME_FORMAT.format(event.getTimestamp().atZone(zone)),
                        isClass ? "Class created" : "Method defined",
                        name});
            }
            table(new String[]{"Elapsed", "Time", "Event", "Name"},
                    new float[]{60, 120, 95, CONTENT_WIDTH - 275}, rows);
        }

        private void drawFooters() {
            int count = pdf.pageCount();
            for (int i = 0; i < count; i++) {
                pdf.selectPage(i);
                pdf.line(MARGIN, 45, MARGIN + CONTENT_WIDTH, 45, 0.5f, RULE);
                pdf.text(MARGIN, 32, 8, false, MUTED, "COMP3607 BlueJ Telemetry");
                String label = "Page " + (i + 1) + " of " + count;
                pdf.text(MARGIN + CONTENT_WIDTH - PdfWriter.textWidth(label, 8, false), 32, 8, false, MUTED, label);
            }
        }

        // ----- Building blocks -----

        /** Starts a new page if fewer than "needed" points are left. */
        private boolean ensure(float needed) {
            if (y - needed < BOTTOM) {
                pdf.newPage();
                y = TOP;
                return true;
            }
            return false;
        }

        private void section(String title) {
            ensure(60);
            y -= 14;
            pdf.text(MARGIN, y - 12, 14, true, ACCENT, title);
            y -= 20;
            pdf.line(MARGIN, y, MARGIN + CONTENT_WIDTH, y, 0.8f, ACCENT);
            y -= 10;
        }

        private void subheading(String title) {
            ensure(40);
            y -= 8;
            pdf.text(MARGIN, y - 10, 11, true, INK, title);
            y -= 20;
        }

        private void note(String text) {
            ensure(16);
            pdf.text(MARGIN, y - 9, 9, false, MUTED, PdfWriter.fit(text, 9, false, CONTENT_WIDTH));
            y -= 16;
        }

        private void legend(float x, float[] color, String label) {
            pdf.fillRect(x, y - 8, 9, 9, color);
            pdf.text(x + 14, y - 7, 9, false, INK, label);
        }

        private void table(String[] headers, float[] widths, List<String[]> rows) {
            ensure(2 * 20);
            drawTableRow(headers, widths, true, false);
            boolean shade = false;
            for (String[] row : rows) {
                if (ensure(20)) {
                    drawTableRow(headers, widths, true, false); // repeat header on the new page
                }
                drawTableRow(row, widths, false, shade);
                shade = !shade;
            }
            y -= 6;
        }

        private void drawTableRow(String[] cells, float[] widths, boolean header, boolean shade) {
            float rowHeight = 20;
            if (header) {
                pdf.fillRect(MARGIN, y - rowHeight, CONTENT_WIDTH, rowHeight, ACCENT);
            } else if (shade) {
                pdf.fillRect(MARGIN, y - rowHeight, CONTENT_WIDTH, rowHeight, SHADE);
            }
            float x = MARGIN;
            for (int i = 0; i < cells.length; i++) {
                String text = PdfWriter.fit(cells[i], 9.5f, header, widths[i] - 12);
                pdf.text(x + 6, y - 14, 9.5f, header, header ? WHITE : INK, text);
                x += widths[i];
            }
            y -= rowHeight;
        }

        /** Horizontal bar chart: one bar per error message, longest first. */
        private void barChart(Map<String, Integer> frequency) {
            int max = frequency.values().stream().mapToInt(Integer::intValue).max().orElse(1);
            float labelWidth = 250f;
            float barAreaWidth = CONTENT_WIDTH - labelWidth - 40;

            int shown = 0;
            for (Map.Entry<String, Integer> entry : frequency.entrySet()) {
                if (shown == MAX_ERRORS_IN_CHART) {
                    break;
                }
                ensure(22);
                pdf.text(MARGIN, y - 13, 9, false, INK,
                        PdfWriter.fit(entry.getKey(), 9, false, labelWidth - 8));
                float barWidth = Math.max(2f, barAreaWidth * entry.getValue() / max);
                pdf.fillRect(MARGIN + labelWidth, y - 16, barWidth, 14, BAD);
                pdf.text(MARGIN + labelWidth + barWidth + 6, y - 13, 9, true, INK,
                        String.valueOf(entry.getValue()));
                y -= 22;
                shown++;
            }
            if (frequency.size() > shown) {
                note("Showing the " + shown + " most common of " + frequency.size() + " different errors.");
            }
        }
    }
}
