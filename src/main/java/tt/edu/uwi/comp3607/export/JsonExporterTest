package tt.edu.uwi.comp3607.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tt.edu.uwi.comp3607.TestClock;
import tt.edu.uwi.comp3607.model.EventFactory;
import tt.edu.uwi.comp3607.model.EventType;
import tt.edu.uwi.comp3607.session.TelemetrySession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonExporterTest {

    private TestClock clock;
    private EventFactory factory;
    private TelemetrySession session;
    private JsonExporter exporter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        clock = TestClock.atEpoch();
        factory = new EventFactory(clock);
        session = new TelemetrySession("816000000", clock);
        exporter = new JsonExporter();
    }

    // ---------- JsonWriter basics ----------

    @Test
    void writesSimpleValues() {
        assertEquals("null", JsonWriter.toJson(null));
        assertEquals("true", JsonWriter.toJson(true));
        assertEquals("42", JsonWriter.toJson(42));
        assertEquals("0.5", JsonWriter.toJson(0.5));
        assertEquals("\"hi\"", JsonWriter.toJson("hi"));
        assertEquals("\"COMPILE_ERROR\"", JsonWriter.toJson(EventType.COMPILE_ERROR));
        assertEquals("\"2026-11-09T10:00:00Z\"",
                JsonWriter.toJson(Instant.parse("2026-11-09T10:00:00Z")));
        assertEquals("[]", JsonWriter.toJson(List.of()));
        assertEquals("{}", JsonWriter.toJson(Map.of()));
    }

    @Test
    void nanAndInfinityBecomeNull() {
        assertEquals("null", JsonWriter.toJson(Double.NaN));
        assertEquals("null", JsonWriter.toJson(Float.POSITIVE_INFINITY));
    }

    @Test
    void specialCharactersAreEscaped() {
        String json = JsonWriter.toJson("say \"hi\"\\ \n\r\t\b\f\u0001");
        assertEquals("\"say \\\"hi\\\"\\\\ \\n\\r\\t\\b\\f\\u0001\"", json);
    }

    @Test
    void unknownObjectsUseToString() {
        Object custom = new Object() {
            @Override
            public String toString() {
                return "custom";
            }
        };
        assertEquals("\"custom\"", JsonWriter.toJson(custom));
    }

    @Test
    void nestedStructuresAreIndented() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("list", List.of("x", "y"));
        String expected = "{\n"
                + "  \"a\": 1,\n"
                + "  \"list\": [\n"
                + "    \"x\",\n"
                + "    \"y\"\n"
                + "  ]\n"
                + "}";
        assertEquals(expected, JsonWriter.toJson(map));
    }

    // ---------- Session export ----------

    @Test
    void containsSessionMetadata() {
        session.record(factory.compileSucceeded("Dog.java"));
        session.record(factory.compileFailed("Dog.java"));
        clock.advanceSeconds(90);
        session.end();

        String json = exporter.toJson(session);

        assertTrue(json.contains("\"schemaVersion\": \"1.0\""));
        assertTrue(json.contains("\"studentId\": \"816000000\""));
        assertTrue(json.contains("\"sessionStart\": \"2026-11-09T10:00:00Z\""));
        assertTrue(json.contains("\"sessionEnd\": \"2026-11-09T10:01:30Z\""));
        assertTrue(json.contains("\"sessionDurationMs\": 90000"));
        assertTrue(json.contains("\"totalCompilationAttempts\": 2"));
        assertTrue(json.contains("\"compileSuccessRate\": 0.5"));
    }

    @Test
    void eventsAreListedWithTimestampTypeAndDetails() {
        session.record(factory.compileError("Dog.java", 7, "';' expected"));
        String json = exporter.toJson(session);

        assertTrue(json.contains("\"type\": \"COMPILE_ERROR\""));
        assertTrue(json.contains("\"timestamp\": \"2026-11-09T10:00:00Z\""));
        assertTrue(json.contains("\"line\": 7"));
        assertTrue(json.contains("\"message\": \"';' expected\""));
    }

    @Test
    void runningSessionHasNullEnd() {
        assertTrue(exporter.toJson(session).contains("\"sessionEnd\": null"));
    }

    @Test
    void outputIsBalancedAndWellFormed() {
        session.record(factory.classCreated("Dog"));
        session.record(factory.compileError("Dog.java", 1, "bad \"quote\" {here}"));
        String json = exporter.toJson(session);

        assertTrue(isBalanced(json), "brackets and quotes should be balanced");
    }

    @Test
    void writesFileAndCreatesFolders() throws IOException {
        session.record(factory.classCreated("Dog"));
        Path target = tempDir.resolve("nested/folder/session.json");

        exporter.export(session, target);

        assertTrue(Files.exists(target));
        String content = Files.readString(target, StandardCharsets.UTF_8);
        assertEquals(exporter.toJson(session), content);
        assertEquals("json", exporter.fileExtension());
    }

    @Test
    void nullAggregatorIsRejected() {
        assertThrows(NullPointerException.class, () -> new JsonExporter(null));
    }

    /** Checks every {, [ and " is properly closed, ignoring characters inside strings. */
    private static boolean isBalanced(String json) {
        List<Character> stack = new ArrayList<>();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                stack.add(c);
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty()) {
                    return false;
                }
                char open = stack.remove(stack.size() - 1);
                if ((c == '}' && open != '{') || (c == ']' && open != '[')) {
                    return false;
                }
            }
        }
        return stack.isEmpty() && !inString;
    }
}