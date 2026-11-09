package tt.edu.uwi.comp3607.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfWriterTest {

    private static final float[] BLACK = {0f, 0f, 0f};

    private static String asText(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Test
    void producesAStructurallyValidPdf() {
        PdfWriter writer = new PdfWriter();
        writer.text(50, 700, 12, false, BLACK, "Hello");

        String pdf = asText(writer.toBytes());

        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.trim().endsWith("%%EOF"));
        assertTrue(pdf.contains("/Count 1"));
        assertTrue(pdf.contains("(Hello) Tj"));
    }

    @Test
    void crossReferenceOffsetsPointAtTheObjects() {
        PdfWriter writer = new PdfWriter();
        writer.text(50, 700, 12, true, BLACK, "Page one");
        writer.newPage();
        writer.text(50, 700, 12, false, BLACK, "Page two");

        String pdf = asText(writer.toBytes());

        int startxref = pdf.lastIndexOf("startxref");
        int xrefOffset = Integer.parseInt(
                pdf.substring(startxref + 10, pdf.indexOf('\n', startxref + 10)).trim());
        assertTrue(pdf.startsWith("xref", xrefOffset));

        String[] lines = pdf.substring(xrefOffset).split("\n");
        int objectCount = Integer.parseInt(lines[1].split(" ")[1]);
        for (int i = 1; i < objectCount; i++) {
            int offset = Integer.parseInt(lines[2 + i].substring(0, 10));
            assertTrue(pdf.startsWith(i + " 0 obj", offset), "object " + i);
        }
    }

    @Test
    void tracksPagesAndSwitchesBetweenThem() {
        PdfWriter writer = new PdfWriter();
        assertEquals(1, writer.pageCount());

        writer.newPage();
        assertEquals(2, writer.pageCount());

        writer.selectPage(0);
        writer.text(10, 10, 8, false, BLACK, "Footer");

        assertTrue(asText(writer.toBytes()).contains("/Count 2"));
        assertThrows(IllegalArgumentException.class, () -> writer.selectPage(2));
        assertThrows(IllegalArgumentException.class, () -> writer.selectPage(-1));
    }

    @Test
    void drawingCommandsAreWrittenToThePage() {
        PdfWriter writer = new PdfWriter();
        writer.fillRect(10, 10, 100, 20, BLACK);
        writer.line(10, 10, 110, 10, 1f, BLACK);

        String pdf = asText(writer.toBytes());

        assertTrue(pdf.contains(" re f"));
        assertTrue(pdf.contains(" l S"));
    }

    @Test
    void escapesPdfSpecialCharacters() {
        assertEquals("a \\(b\\) \\\\ c", PdfWriter.escape("a (b) \\ c"));
    }

    @Test
    void replacesCharactersThatCannotBeShown() {
        assertEquals("é?", PdfWriter.sanitise("é→"));
        assertEquals("a b", PdfWriter.sanitise("a\tb"));
        assertEquals("?", PdfWriter.sanitise("\n"));
        assertEquals("", PdfWriter.sanitise(null));
    }

    @Test
    void textWidthReflectsGlyphsAndBold() {
        assertTrue(PdfWriter.textWidth("iii", 10, false) < PdfWriter.textWidth("WWW", 10, false));
        assertTrue(PdfWriter.textWidth("Hello", 10, true) > PdfWriter.textWidth("Hello", 10, false));
        assertEquals(0f, PdfWriter.textWidth("", 10, false));
    }

    @Test
    void fitShortensLongTextWithAnEllipsis() {
        String longText = "This is a rather long sentence";
        String fitted = PdfWriter.fit(longText, 10, false, 60);

        assertTrue(fitted.endsWith("..."));
        assertTrue(PdfWriter.textWidth(fitted, 10, false) <= 60);
        assertEquals("Short", PdfWriter.fit("Short", 10, false, 200));
        assertEquals("...", PdfWriter.fit(longText, 10, false, 1));
    }
}
