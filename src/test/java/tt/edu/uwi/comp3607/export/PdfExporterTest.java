package tt.edu.uwi.comp3607.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tt.edu.uwi.comp3607.TestClock;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.report.ReportAggregator;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExporterTest {

    @TempDir
    Path tempDir;

    private TestClock clock;
    private TelemetrySession session;
    private EventFactory factory;
    private PdfExporter exporter;

    @BeforeEach
    void setUp() {
        clock = TestClock.atEpoch();
        session = new TelemetrySession("816012345", clock);
        factory = new EventFactory(clock);
        exporter = new PdfExporter(new ReportAggregator(), ZoneOffset.UTC);
    }

    private String render() {
        return new String(exporter.toBytes(session), StandardCharsets.ISO_8859_1);
    }

    @Test
    void reportsItsFileExtension() {
        assertEquals("pdf", exporter.fileExtension());
    }

    @Test
    void constructorsRejectNulls() {
        assertThrows(NullPointerException.class, () -> new PdfExporter(null));
        assertThrows(NullPointerException.class, () -> new PdfExporter(new ReportAggregator(), null));
        assertThrows(NullPointerException.class, () -> exporter.toBytes(null));
    }

    @Test
    void emptySessionStillProducesAReadableReport() {
        String pdf = render();

        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.contains("BlueJ Telemetry Report"));
        assertTrue(pdf.contains("Student ID: 816012345"));
        assertTrue(pdf.contains("No session time was recorded."));
        assertTrue(pdf.contains("No compilation errors were recorded."));
        assertTrue(pdf.contains("No classes or methods were created in this session."));
        assertTrue(pdf.contains("Still running"));
    }

    @Test
    void reportShowsTheCalculatedFigures() {
        session.record(factory.classCreated("Cat"));
        session.record(factory.documentEdit("Cat", 3, 0, -1));
        clock.advanceSeconds(30);
        session.record(factory.documentEdit("Cat", 3, 0, 30000));
        session.record(factory.methodAdded("Cat", "speak() : void"));
        session.record(factory.compileError("Cat.java", 4, "cannot find symbol"));
        session.record(factory.compileFailed("Cat.java"));
        session.record(factory.compileSucceeded("Cat.java"));
        session.record(factory.programRun("Cat", "main"));
        clock.advanceSeconds(90);
        session.end();

        String pdf = render();

        assertTrue(pdf.contains("2026-11-09 10:00:00"));
        assertTrue(pdf.contains("Total session duration"));
        assertTrue(pdf.contains("2m 00s"));
        assertTrue(pdf.contains("Compilation attempts"));
        assertTrue(pdf.contains("50.0%"));
        assertTrue(pdf.contains("cannot find symbol"));
        assertTrue(pdf.contains("Class created"));
        assertTrue(pdf.contains("Method defined"));
        assertTrue(pdf.contains("Cat.speak\\(\\) : void")); // brackets are escaped inside a PDF string
        assertTrue(pdf.contains("Active: 30s \\(25.0%\\)"));
        assertTrue(pdf.contains("Idle: 1m 30s \\(75.0%\\)"));
        assertFalse(pdf.contains("Still running"));
    }

    @Test
    void errorChartShowsOnlyTheMostCommonErrors() {
        for (int i = 0; i < 12; i++) {
            session.record(factory.compileError("A.java", i, "error number " + i));
        }
        clock.advanceSeconds(10);

        assertTrue(render().contains("Showing the 10 most common of 12 different errors."));
    }

    @Test
    void longTimelinesContinueOnExtraPages() {
        for (int i = 0; i < 100; i++) {
            clock.advanceSeconds(1);
            session.record(factory.classCreated("Class" + i));
        }

        String pdf = render();

        assertTrue(pdf.contains("Page 2 of"));
        assertTrue(pdf.contains("Class99"));
    }

    @Test
    void exportWritesAPdfFileAndCreatesFolders() throws IOException {
        session.record(factory.create(EventType.SESSION_STARTED));
        Path target = tempDir.resolve("nested").resolve("report.pdf");

        exporter.export(session, target);

        assertTrue(Files.exists(target));
        byte[] written = Files.readAllBytes(target);
        assertTrue(written.length > 0);
        assertTrue(new String(written, StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"));
    }

    @Test
    void formatsDurations() {
        assertEquals("0s", PdfExporter.formatDuration(Duration.ZERO));
        assertEquals("45s", PdfExporter.formatDuration(Duration.ofSeconds(45)));
        assertEquals("1m 05s", PdfExporter.formatDuration(Duration.ofSeconds(65)));
        assertEquals("1h 02m 05s", PdfExporter.formatDuration(Duration.ofSeconds(3725)));
        assertEquals("0s", PdfExporter.formatDuration(Duration.ofSeconds(-5)));
    }

    @Test
    void formatsPercentagesAndOffsets() {
        assertEquals("50.0%", PdfExporter.formatPercent(0.5));
        assertEquals("0.0%", PdfExporter.formatPercent(0));
        assertEquals("+01:15", PdfExporter.formatOffset(Duration.ofSeconds(75)));
        assertEquals("+00:00", PdfExporter.formatOffset(Duration.ofSeconds(-3)));
    }
}
